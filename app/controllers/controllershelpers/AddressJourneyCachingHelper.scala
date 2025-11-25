/*
 * Copyright 2025 HM Revenue & Customs
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

package controllers.controllershelpers

import com.google.inject.{Inject, Singleton}
import controllers.auth.requests.UserRequest
import controllers.bindable.AddrType
import models._
import play.api.Logging
import play.api.libs.json.Writes
import play.api.mvc.{Result, Results}
import repositories.JourneyCacheRepository
import routePages._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AddressJourneyCachingHelper @Inject() (val journeyCacheRepository: JourneyCacheRepository)(implicit
  ec: ExecutionContext
) extends Results
    with Logging {

  def addToCache[A: Writes](page: QuestionPage[A], record: A)(implicit request: UserRequest[_]): Future[UserAnswers] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    journeyCacheRepository.get(hc).flatMap { userAnswers =>
      val updatedAnswers = userAnswers.setOrException(page, record)
      journeyCacheRepository.set(updatedAnswers).map(_ => updatedAnswers)
    }
  }

  def clearCache()(implicit request: UserRequest[_]): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    journeyCacheRepository.clear(hc)
  }

  def gettingCachedJourneyData(typ: AddrType)(implicit hc: HeaderCarrier): Future[AddressJourneyData] =
    journeyCacheRepository.get.map { userAnswers =>
      AddressJourneyData(
        userAnswers.get(HasAddressAlreadyVisitedPage),
        userAnswers.get(SubmittedResidencyChoicePage(typ)),
        userAnswers.get(SelectedRecordSetPage(typ)),
        userAnswers.get(AddressFinderPage(typ)),
        userAnswers.get(SelectedAddressRecordPage(typ)),
        userAnswers.get(SubmittedAddressPage(typ)),
        userAnswers.get(SubmittedInternationalAddressChoicePage),
        userAnswers.get(SubmittedStartDatePage(typ))
      )
    }

  def gettingCachedJourneyData[T](
    typ: AddrType
  )(block: AddressJourneyData => Future[T])(implicit request: UserRequest[_]): Future[T] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    journeyCacheRepository
      .get(hc)
      .flatMap { userAnswers =>
        block(
          AddressJourneyData(
            userAnswers.get(HasAddressAlreadyVisitedPage),
            userAnswers.get(SubmittedResidencyChoicePage(typ)),
            userAnswers.get(SelectedRecordSetPage(typ)),
            userAnswers.get(AddressFinderPage(typ)),
            userAnswers.get(SelectedAddressRecordPage(typ)),
            userAnswers.get(SubmittedAddressPage(typ)),
            userAnswers.get(SubmittedInternationalAddressChoicePage),
            userAnswers.get(SubmittedStartDatePage(typ))
          )
        )
      }
  }

  def enforceDisplayAddressPageVisited(result: Result)(implicit request: UserRequest[_]): Future[Result] = {
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    journeyCacheRepository.get(hc).map { userAnswers =>
      userAnswers.get(HasAddressAlreadyVisitedPage) match {
        case Some(_) =>
          logger.info("Has address already visited present")
          result
        case None    =>
          logger.info("Has address already visited NOT present")
          Redirect(controllers.address.routes.PersonalDetailsController.onPageLoad)
      }
    }
  }
}
