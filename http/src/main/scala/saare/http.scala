/*Copyright 2014 sumito3478 <sumito3478@gmail.com>

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

import java.util.{ concurrent => juc }
import java.io._

import scala.concurrent._
import scala.util._
import scala.util.matching._

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

trait Handler[A] {
  private[saare] def handler: AsyncHandler[A]
}
object NullHandler extends Handler[Unit] {
  def handler = new AsyncCompletionHandler[Unit] {
    override def onCompleted(response: Response) = ()
  }
}
object StringHandler extends Handler[String] {
  def handler = new AsyncCompletionHandler[String] {
    @volatile private[this] var charset = "UTF-8"
    override def onHeadersReceived(headers: HttpResponseHeaders) = {
      for {
        contentType <- Option(headers.getHeaders.getFirstValue("Content-Type"))
        charset <- Option(AsyncHttpProviderUtils.parseCharset(contentType))
      } this.charset = charset
      CONTINUE
    }
    override def onCompleted(response: Response): String = response.getResponseBody(charset)
  }
}
class StringStreamHandler(f: Seq[String] => Boolean /* continue */ , delimiter: Regex) extends Handler[Unit] {
  def handler = new AsyncHandler[Unit] {
    @volatile private[this] var charset = "UTF-8"
    @volatile private[this] var state = CONTINUE
    @volatile private[this] var buf = ""
    override def onThrowable(t: Throwable) = ()
    override def onStatusReceived(status: HttpResponseStatus) = state
    override def onHeadersReceived(headers: HttpResponseHeaders) = {
      for {
        contentType <- Option(headers.getHeaders.getFirstValue("Content-Type"))
        charset <- Option(AsyncHttpProviderUtils.parseCharset(contentType))
      } this.charset = charset
      state
    }
    override def onBodyPartReceived(bodyPart: HttpResponseBodyPart) = {
      if (state == CONTINUE) {
        val fragments = delimiter.split(buf + new String(bodyPart.getBodyPartBytes, charset))
        val continue = f(fragments.take(fragments.length - 1))
        if (!continue)
          state = ABORT
        buf = fragments.last
      }
      state
    }
    override def onCompleted() = {
      f(Seq(buf))
    }
  }
}
class LineStreamHandler(f: Seq[String] => Boolean) extends StringStreamHandler(f, "[\n\r]+".r)
case class Request[A, B: RequestBody](url: String, method: String = "GET", body: B = EmptyRequestBody, headers: Map[String, String] = Map(), queries: Map[String, String] = Map(), handler: Handler[A]) {
  private[http] def toUnderlying = {
    val builder = new RequestBuilder
    builder.setUrl(url)
    builder.setMethod(method)
    builder |> implicitly[RequestBody[B]].build(body)
    for ((k, v) <- headers) builder.addHeader(k, v)
    for ((k, v) <- queries) builder.addQueryParameter(k, v)
    builder.build
  }
  private[http] def toAsyncHandler = handler.handler
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
}
