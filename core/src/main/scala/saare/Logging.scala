/*Copyright 2013 sumito3478 <sumito3478@gmail.com>

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

import scala.language.experimental.macros
trait Logging {
  self =>
  object logger {
    val underlying = org.slf4j.LoggerFactory.getLogger(self.getClass.getName)
    def error(msg: String): Unit = macro Macros.Logger.errorImpl
    def error(msg: String, e: Throwable): Unit = macro Macros.Logger.errorThrowableImpl
    def warn(msg: String): Unit = macro Macros.Logger.warnImpl
    def warn(msg: String, e: Throwable): Unit = macro Macros.Logger.warnThrowableImpl
    def info(msg: String): Unit = macro Macros.Logger.infoImpl
    def info(msg: String, e: Throwable): Unit = macro Macros.Logger.infoThrowableImpl
    def debug(msg: String): Unit = macro Macros.Logger.debugImpl
    def debug(msg: String, e: Throwable): Unit = macro Macros.Logger.debugThrowableImpl
    def trace(msg: String): Unit = macro Macros.Logger.traceImpl
    def trace(msg: String, e: Throwable): Unit = macro Macros.Logger.traceThrowableImpl
  }
}
