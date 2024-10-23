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

package util

import config.ConfigDecorator
import models.{Activated, ItsaEnrolmentEnrolled, NotYetActivated, SelfAssessmentEnrolment}
import play.api.http.Status.INTERNAL_SERVER_ERROR
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr}

import scala.util.Random

class EnrolmentsHelperSpec extends BaseSpec {
  val sut: EnrolmentsHelper                                 = inject[EnrolmentsHelper]
  val nino: Nino                                            = Fixtures.fakeNino
  implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
  implicit val configDecorator: ConfigDecorator             = app.injector.instanceOf[ConfigDecorator]
  implicit lazy val messages: Messages                      = MessagesImpl(Lang("en"), messagesApi)

  "singleAccountEnrolmentPresent" when {
    "enrolment is present and nino matches" must {
      "returns Right(true)" in {
        val result = sut.singleAccountEnrolmentPresent(
          Set(
            Enrolment(
              "HMRC-PT",
              Seq(
                EnrolmentIdentifier("NINO", nino.nino),
                EnrolmentIdentifier("NINO", new Generator(new Random()).nextNino.nino)
              ),
              "Activated"
            )
          ),
          nino
        )

        result mustBe Right(true)
      }
    }

    "enrolment is present and nino does not match" must {
      "returns Left(InternalServerError)" in {
        val result = sut.singleAccountEnrolmentPresent(
          Set(
            Enrolment(
              "HMRC-PT",
              Seq(EnrolmentIdentifier("NINO", new Generator(new Random()).nextNino.nino)),
              "Activated"
            )
          ),
          nino
        )

        result mustBe a[Left[Result, _]]
        result.swap.getOrElse(Ok("")).header.status mustBe INTERNAL_SERVER_ERROR
      }
    }

    "enrolment is not present" must {
      "returns Right(false)" in {
        val result = sut.singleAccountEnrolmentPresent(
          Set(
            Enrolment(
              "IR-SA",
              Seq(EnrolmentIdentifier("utr", "random utr")),
              "Activated"
            )
          ),
          nino
        )

        result mustBe Right(false)
      }
    }
  }

  "selfAssessmentStatus" when {
    "TrustedHelper is None" when {
      "no sa enrolment is present" must {
        "returns None" in {
          val result = sut.selfAssessmentStatus(
            Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", nino.nino)), "Activated")),
            None
          )

          result mustBe None
        }
      }

      List(Activated, NotYetActivated).foreach { status =>
        s"sa enrolment is present and is $status" must {
          "returns SelfAssessment status" in {
            val result = sut.selfAssessmentStatus(
              Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "utr")), status.toString)),
              None
            )

            result mustBe Some(SelfAssessmentEnrolment(SaUtr("utr"), status))
          }
        }
      }

      "sa enrolment is present and is invalid" must {
        "returns SelfAssessment status" in {
          val result = intercept[RuntimeException](
            sut.selfAssessmentStatus(
              Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "utr")), "invalidState")),
              None
            )
          )

          result.getMessage mustBe "Unexpected enrolment status of invalidState was returned"
        }
      }
    }

    "TrustedHelper is set" must {
      "returns None" when {
        "no sa enrolment is present" in {
          val result = sut.selfAssessmentStatus(
            Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", nino.nino)), "Activated")),
            Some(TrustedHelper("principalName", "attorneyName", "returnLinkUrl", Some("principalNino")))
          )

          result mustBe None
        }

        List(Activated, NotYetActivated).foreach { status =>
          s"sa enrolment is present and is $status" in {
            val result = sut.selfAssessmentStatus(
              Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "utr")), status.toString)),
              Some(TrustedHelper("principalName", "attorneyName", "returnLinkUrl", Some("principalNino")))
            )

            result mustBe None
          }
        }

        "sa enrolment is present and is invalid" in {
          val result = sut.selfAssessmentStatus(
            Set(Enrolment("IR-SA", Seq(EnrolmentIdentifier("UTR", "utr")), "invalidState")),
            Some(TrustedHelper("principalName", "attorneyName", "returnLinkUrl", Some("principalNino")))
          )

          result mustBe None
        }
      }
    }
  }

  "itsaEnrolmentStatus" when {
    "no sa enrolment is present" must {
      "returns None" in {
        val result = sut.itsaEnrolmentStatus(
          Set(Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", nino.nino)), "Activated"))
        )

        result mustBe None
      }
    }

    List(Activated, NotYetActivated).foreach { status =>
      s"sa enrolment is present and is $status" must {
        "returns ItsaEnrolment status" in {
          val result = sut.itsaEnrolmentStatus(
            Set(Enrolment("HMRC-MTD-IT", Seq(EnrolmentIdentifier("MTDITID", "id")), status.toString))
          )

          result mustBe Some(ItsaEnrolmentEnrolled(status))
        }
      }
    }

    "sa enrolment is present and is invalid" must {
      "returns ItsaEnrolment status" in {
        val result = intercept[RuntimeException](
          sut.itsaEnrolmentStatus(
            Set(Enrolment("HMRC-MTD-IT", Seq(EnrolmentIdentifier("MTDITID", "id")), "invalidState"))
          )
        )

        result.getMessage mustBe "Unexpected enrolment status of invalidState was returned"
      }
    }
  }

}
