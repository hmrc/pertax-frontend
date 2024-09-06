/*
 * Copyright 2023 HM Revenue & Customs
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

package models.enrolments

object EnrolmentEnum extends Enumeration {

  val hmrcPTKey: EnrolmentEnum.Value    = Value("HMRC-PT")
  val IRSAKey: EnrolmentEnum.Value      = Value("IR-SA")
  val hmrcMTDITKey: EnrolmentEnum.Value = Value("HMRC-MTD-IT")
  val hmrcNIKey: EnrolmentEnum.Value    = Value("HMRC-NI")

  val saEnrolmentSet: Set[String] =
    Set(IRSAKey.toString, hmrcMTDITKey.toString, hmrcNIKey.toString)
}
