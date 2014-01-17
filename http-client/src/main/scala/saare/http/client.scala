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
package client

import scala.language.implicitConversions

import scala.collection._
import scala.concurrent._

import com.ning.http.{ client => ahc }

import io.netty.buffer._

import akka.util.ByteString

import saare._, Saare._
import saare.json._, Json._

class Client(userAgent: Option[String] = None) extends Disposable[Client] {
  client =>
  private[this] val underlying =
    dispatch.Http.configure {
      builder =>
        for (userAgent <- userAgent)
          builder.setUserAgent(userAgent)
        builder
    }
  def disposeInternal() = underlying.shutdown
  object ProxyServer {
    object Protocol {
      case object Http extends Protocol
      case object Https extends Protocol
    }
    import Protocol._
    sealed trait Protocol {
      private[client] def toUnderlying = this match {
        case Http => ahc.ProxyServer.Protocol.HTTP
        case Https => ahc.ProxyServer.Protocol.HTTPS
      }
    }
  }
  case class Credential(user: String, password: String)
  case class ProxyServer(protocol: ProxyServer.Protocol, host: String, port: Int, credential: Option[Credential]) {
    private[client] def toUnderlying = credential match {
      case Some(credential) => new ahc.ProxyServer(protocol.toUnderlying, host, port, credential.user, credential.password)
      case None => new ahc.ProxyServer(protocol.toUnderlying, host, port)
    }
  }
  class Request(private[client] val underlying: dispatch.Req)
  object Request {
    private[this] implicit def request2underlying(request: Request): dispatch.Req = request.underlying
    private[this] implicit def underlying2request(underlying: dispatch.Req): Request = new Request(underlying = underlying)
    type Verb = Request => Request
    def HEAD: Verb = _.HEAD
    def GET: Verb = _.GET
    def POST: Verb = _.POST
    def PUT: Verb = _.PUT
    def DELETE: Verb = _.DELETE
    def PATCH: Verb = _.PATCH
    def TRACE: Verb = _.TRACE
    def OPTIONS: Verb = _.OPTIONS
    def segment(x: String): Verb = _ / x
    def secure: Verb = _.secure
    def headers(xs: (String, String)*): Verb = _ <:< xs
    def params(xs: (String, String)*): Verb = _ << xs
    def stringBody(x: String): Verb = _ << x
    def fileBody(x: java.io.File): Verb = _ <<< x
    def queries(xs: (String, String)*): Verb = _ <<? xs
    def followRedirects(x: Boolean): Verb = _ setFollowRedirects x
    def proxy(x: ProxyServer): Verb = _ setProxyServer x.toUnderlying
    def basicAuth(x: Credential): Verb = _ as_! (x.user, x.password)

    def apply(url: String) = new Request(dispatch.url(url))
  }
  type Handle[A] = Request => Future[A]
  trait Handler[A] extends Handle[A] {
    private[client] def underlying: Either[ahc.Response => A, ahc.AsyncHandler[A]]
    override def apply(x) = underlying match {
      case Left(handler) => client.underlying(x.underlying > handler)
      case Right(handler) => client.underlying(x.underlying > handler)
    }
  }
  object Handler {
    private[this] implicit def asyncHandler2handler[A](asyncHandler: ahc.AsyncHandler[A]): Handle[A] = new Handler[A] { def underlying = Right(asyncHandler) }
    private[this] implicit def function2handler[A](f: ahc.Response => A): Handle[A] = new Handler[A] { def underlying = Left(f) }
    val string: Handle[String] = dispatch.as.String
    def file(x: java.io.File): Handle[_] = asyncHandler2handler(dispatch.as.File(x))
    val byteString: Handle[ByteString] = new CallbackHandler[ByteString, ByteString] {
      def init = ByteString.empty
      def status = (buf, status) => buf
      def headers = (buf, hs) => buf
      def body = (buf, bytebuf) => buf ++ ByteString(bytebuf.nioBuffer)
      def completion = buf => buf
    }
  }
  case class Status(code: Int, text: String)
  trait CallbackHandler[A, B] extends Handler[B] {
    def init: A
    def status: (A, Status) => A
    def headers: (A, Map[String, String]) => A
    def body: (A, ByteBuf) => A
    def completion: A => B
    override def underlying = Right(new ahc.AsyncHandler[B] {
      import ahc.AsyncHandler.STATE._
      @volatile
      private[this] var state = init
      override def onThrowable(t) = ()
      override def onStatusReceived(responseStatus) = {
        state = status(state, Status(code = responseStatus.getStatusCode, text = responseStatus.getStatusText))
        CONTINUE
      }
      override def onHeadersReceived(responseHeaders) = {
        import scala.collection.JavaConversions._
        // - should multiple values for a single key be supported?
        // - case insensitive Ordering[String] should be exist in saare-core or in a new module saare-collection
        val hs = immutable.TreeMap[String, String](responseHeaders.getHeaders.mapValues(_.head).toSeq: _*)(new Ordering[String] {
          def compare(a, b) = a compareToIgnoreCase b
        })
        state = headers(state, hs)
        CONTINUE
      }
      override def onBodyPartReceived(bodyPart) = {
        state = body(state, Unpooled.wrappedBuffer(bodyPart.getBodyByteBuffer))
        CONTINUE
      }
      override def onCompleted = completion(state)
    })
  }
}
