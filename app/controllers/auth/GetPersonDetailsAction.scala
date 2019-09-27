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

package controllers.auth

import com.google.inject.Inject
import controllers.auth.requests.{RefinedRequest, UserRequest}
import models.PersonDetails
import play.api.mvc.{ActionFunction, ActionRefiner, Result}
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, PersonDetailsHiddenResponse, PersonDetailsSuccessResponse}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import play.api.mvc.Results.Locked
import play.api.i18n.{I18nSupport, MessagesApi}

import scala.concurrent.{ExecutionContext, Future}

class GetPersonDetailsAction @Inject()(
  citizenDetailsService: CitizenDetailsService,
  messageFrontendService: MessageFrontendService,
  val messagesApi: MessagesApi)(implicit ec: ExecutionContext)
    extends ActionRefiner[RefinedRequest, UserRequest] with ActionFunction[RefinedRequest, UserRequest]
    with I18nSupport {
  override protected def refine[A](request: RefinedRequest[A]): Future[Either[Result, UserRequest[A]]] =
    populatingUnreadMessageCount()(request).flatMap { messageCount =>
      if (!request.uri.contains("/signout")) {
        getPersonDetails()(request).map { a =>
          a.fold(
            Left(_),
            pd =>
              Right(
                UserRequest(
                  request.nino,
                  request.name,
                  request.previousLoginTime,
                  request.saUserType,
                  request.authProvider,
                  request.confidenceLevel,
                  pd,
                  messageCount,
                  request.activeTab,
                  request.request
                ))
          )
        }
      } else {
        Future.successful(
          Right(UserRequest(
            request.nino,
            request.name,
            request.previousLoginTime,
            request.saUserType,
            request.authProvider,
            request.confidenceLevel,
            None,
            messageCount,
            request.activeTab,
            request.request
          )))
      }
    }

  def populatingUnreadMessageCount()(implicit request: RefinedRequest[_]): Future[Option[Int]] =
    messageFrontendService.getUnreadMessageCount

  private def getPersonDetails()(implicit request: RefinedRequest[_]): Future[Either[Result, Option[PersonDetails]]] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromHeadersAndSession(request.headers, Some(request.session))

    request.nino match {
      case Some(nino) if request.isGovernmentGateway =>
        citizenDetailsService.personDetails(nino).map {
          case PersonDetailsSuccessResponse(pd) => Right(Some(pd))
          case PersonDetailsHiddenResponse =>
            Left(Locked(views.html.manualCorrespondence()))
          case _ => Right(None)
        }
      case _ => Future.successful(Right(None))
    }
  }

}
