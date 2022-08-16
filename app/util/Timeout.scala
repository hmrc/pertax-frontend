/*
 * Copyright 2022 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package util

import scala.concurrent._
import scala.concurrent.duration._
import akka.actor.ActorSystem
import play.api.Logging

case object FutureEarlyTimeout extends RuntimeException

trait Timeout extends Logging {
  private val system: ActorSystem = ActorSystem()

  def withTimeout[A](timeoutDuration: FiniteDuration)(block: => Future[A])(implicit ec: ExecutionContext): Future[A] = {
    val delayedFuture =
      akka.pattern.after(timeoutDuration, system.scheduler) {
        val exception = new RuntimeException(s"Future took longer than ${timeoutDuration.toSeconds}s")
        logger.error(exception.getMessage + "\n" + exception.getStackTrace.mkString("\n"))
        Future.failed(FutureEarlyTimeout)
      }

    Future.firstCompletedOf(Seq(block, delayedFuture))
  }
}
