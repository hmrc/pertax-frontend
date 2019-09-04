/*
 * Copyright 2019 HM Revenue & Customs
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

import org.scalactic._
import org.scalatest.exceptions.{StackDepthException, TestFailedException}

object BetterOptionValues {
  implicit class OptionOps[T](val opt: Option[T]) extends AnyVal {

    def getValue(implicit pos: source.Position): T =
      try {
        opt.get
      } catch {
        case cause: NoSuchElementException =>
          throw new TestFailedException(
            (_: StackDepthException) => Some("The Option on which value was invoked was not defined."),
            Some(cause),
            pos)
      }
  }
}
