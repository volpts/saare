/*Copyright 2013-2014 sumito3478 <sumito3478@gmail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package saare
package http

import scala.language.existentials

import java.util.{ concurrent => juc }
import java.io._
import java.nio.charset._

import scala.concurrent._
import scala.util._
import scala.util.matching._
import scala.util.control._
import scala.util.control.Exception._
import scala.collection._
import scala.math._

import com.ning.http.{ client => ahc, util => ahcUtil }
import ahc._
import ahcUtil._
import ahc.generators._
import ahc.providers.netty.NettyAsyncHttpProviderConfig
import AsyncHandler.STATE._

import org.jboss.{ netty => netty3 }
import netty3.channel.socket.nio._
import netty3.util._

import io.{ netty => netty4 }
import netty4.buffer._

import saare._, Saare._
import saare.json._, Json._
import Http._

class Client(userAgent: Option[String] = None) extends Disposable[Client] {
  // Need to dispose on interrupt, otherwise sbt console exists with high cpu usage.
  // See https://github.com/dispatch/reboot/issues/58
  private[this] val es = newDefaultExecutorService(Some(this))
  private[this] val channelFactory = new NioClientSocketChannelFactory(es, 1,
    new NioWorkerPool(es, Runtime.getRuntime.availableProcessors * 2), new HashedWheelTimer(newDefaultThreadFactory()))
  private[this] val underlying = {
    val builder = new AsyncHttpClientConfig.Builder()
    builder.setAsyncHttpClientProviderConfig(new NettyAsyncHttpProviderConfig().addProperty(NettyAsyncHttpProviderConfig.SOCKET_CHANNEL_FACTORY, channelFactory))
    for (userAgent <- userAgent)
      builder.setUserAgent(userAgent)
    builder.setRequestTimeoutInMs(-1)
    new AsyncHttpClient(builder.build)
  }
  def disposeInternal = {
    underlying.close
    channelFactory.releaseExternalResources
  }
  def submit[A]: Request[A, _] => Future[A] = request => {
    val f = underlying.executeRequest(request.toUnderlying, request.toAsyncHandler)
    val p = Promise[A]()
    f.completed(p.complete _)
    p.future
  }
}
trait RequestBody[A] {
  private[saare] def build(x: A): RequestBuilder => Unit
}

object EmptyRequestBody

sealed trait ResponsePart

case class Status(code: Int, reason: String) extends ResponsePart
case class Headers private (headers: Map[String, String]) extends ResponsePart
object Headers {
  def apply(xs: (String, String)*): Headers = Headers(immutable.TreeMap[String, String](xs: _*)(new Ordering[String] {
    def compare(x, y) = x compareToIgnoreCase y
  }))
}
case class Content(content: ByteBuf) extends ResponsePart

trait Handler[A, B] extends Logging[Handler[A, B]] {
  self =>
  def init: A
  def handle: (A, ResponsePart) => Try[A]
  def completed: A => B
  def andThen[C](f: B => C) = new Handler[A, C] {
    def init = self.init
    def handle = self.handle
    def completed = self.completed.andThen(f)
  }

  private[http] def toUnderlying = new AsyncHandler[B] {
    @volatile private[this] var acc: Try[A] = Success(init)
    private[this] def state = if (acc.isSuccess) CONTINUE else ABORT
    override def onThrowable(t) = logger.error("Exception occurred while handling http response", t)
    override def onStatusReceived(status) = {
      acc = acc.flatMap(handle(_, Status(code = status.getStatusCode, reason = status.getStatusText)))
      state
    }
    override def onHeadersReceived(headers) = {
      import scala.collection.JavaConversions._
      acc = acc.flatMap(handle(_, Headers(headers.getHeaders.iterator.toSeq.map(e => e.getKey -> e.getValue.head): _*)))
      state
    }
    override def onBodyPartReceived(bodyPart) = {
      acc = acc.flatMap(handle(_, Content(content = Unpooled.wrappedBuffer(bodyPart.getBodyByteBuffer))))
      state
    }
    override def onCompleted() = {
      acc match {
        case Success(result) => completed(result)
        case Failure(e) => throw e
      }
    }
  }
}
object NullHandler extends Handler[Unit, Unit] {
  override def init = ()
  override def handle = { case ((), _) => Success(()) }
  override def completed = _ => ()
}
class StringHandler(forceCharset: Option[Charset] = None) extends Handler[(Option[Charset], mutable.UnrolledBuffer[String]), String] {
  override def init = (None, new mutable.UnrolledBuffer[String])
  override def handle = {
    case (acc, Status(_, _)) => Success(acc)
    case ((charset, buf), Headers(headers)) =>
      Success(for {
        contentType <- headers.get("Content-Type")
        charset <- Option(AsyncHttpProviderUtils.parseCharset(contentType))
      } yield Charset.forName(charset), buf)
    case ((charset, buf), Content(content)) =>
      // Use iso-8859-1 by default to avoid data loss
      // TODO: Use CharsetDetector of ICU4J to auto-detect charset
      val c = forceCharset.getOrElse(charset.getOrElse(Charset.forName("iso-8859-1")))
      Success((charset, buf += content.toString(c)))
  }
  override def completed = {
    case (charset, buf) => buf.mkString
  }
}
class StringStreamHandler(f: Seq[String] => Unit, delimiter: Regex, forceCharset: Option[Charset] = None) extends Handler[(Option[Charset], String), Unit] {
  override def init = (None, "")
  override def handle = {
    case (acc, Status(_, _)) => Success(acc)
    case ((charset, buf), Headers(headers)) =>
      Success(for {
        contentType <- headers.get("Content-Type")
        charset <- Option(AsyncHttpProviderUtils.parseCharset(contentType))
      } yield Charset.forName(charset), buf)
    case ((charset, buf), Content(content)) =>
      // Use iso-8859-1 by default to avoid data loss
      // TODO: Use CharsetDetector of ICU4J to auto-detect charset
      val c = forceCharset.getOrElse(charset.getOrElse(Charset.forName("iso-8859-1")))
      val fragments = delimiter.split(buf + content.toString(c))
      allCatch.withTry(f(fragments.take(fragments.length - 1))).map(_ => (charset, fragments.last))
  }
  def completed = {
    case (charset, buf) =>
      if (!buf.isEmpty)
        f(Seq(buf))
      ()
  }
}
class LineStreamHandler(f: Seq[String] => Unit, forceCharset: Option[Charset] = None) extends StringStreamHandler(f = f, delimiter = "[\n\r]+".r, forceCharset = forceCharset)
case class Request[A, B: RequestBody](url: String, method: String = "GET", body: B = EmptyRequestBody, headers: Map[String, String] = Map(), queries: Map[String, String] = Map(), handler: Handler[_, A]) {
  private[http] def toUnderlying = {
    val builder = new RequestBuilder
    builder.setUrl(url)
    builder.setMethod(method)
    builder |> implicitly[RequestBody[B]].build(body)
    for ((k, v) <- headers) builder.addHeader(k, v)
    for ((k, v) <- queries) builder.addQueryParameter(k, v)
    builder.build
  }
  private[http] def toAsyncHandler = handler.toUnderlying
}
object Http {
  implicit val emptyRequestBodyIsRequestBody = new RequestBody[EmptyRequestBody.type] {
    def build(x: EmptyRequestBody.type) = req => ()
  }
  implicit val stringIsRequestBody = new RequestBody[String] {
    def build(x: String) = req => req.setBody(x)
  }
  implicit val fileIsRequestBody = new RequestBody[java.io.File] {
    def build(x: File) = req => req.setBody(x)
  }
  implicit val byteBufIsRequestBody = new RequestBody[ByteBuf] {
    def build(x: ByteBuf) = req => req.setBody(new InputStreamBodyGenerator(new ByteBufInputStream(x)))
  }
  def headers[A, B: RequestBody](xs: (String, String)*): Request[A, B] => Request[A, B] = req => req.copy[A, B](headers = req.headers ++ xs)

  def queries[A, B: RequestBody](xs: (String, String)*): Request[A, B] => Request[A, B] = req => req.copy[A, B](queries = req.queries ++ xs)

  private[http] implicit class AHCFutureOps[A](self: ahc.ListenableFuture[A]) {
    def completed[U](f: Try[A] => U): Unit = self.addListener(new Runnable { def run = f(Try(self.get)) }, new juc.Executor { def execute(r: Runnable): Unit = ec.execute(r) })
  }
  def JsonHandler[A: Decoder](forceCharset: Option[Charset] = None) = new StringHandler(forceCharset = forceCharset).andThen(str => decode[A]((str |> parse).get).get)
}
