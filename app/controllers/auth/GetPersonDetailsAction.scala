/*
 * Copyright 2021 HM Revenue & Customs
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
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.PersonDetails
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results.Locked
import play.api.mvc.{ActionFunction, ActionRefiner, ControllerComponents, Result}
import services.partials.MessageFrontendService
import services.{CitizenDetailsService, PersonDetailsHiddenResponse, PersonDetailsSuccessResponse}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.renderer.TemplateRenderer
import util.LocalPartialRetriever
import views.html.ManualCorrespondenceView

import scala.concurrent.{ExecutionContext, Future}

class GetPersonDetailsAction @Inject()(
  citizenDetailsService: CitizenDetailsService,
  messageFrontendService: MessageFrontendService,
  cc: ControllerComponents,
  val messagesApi: MessagesApi,
  manualCorrespondenceView: ManualCorrespondenceView)(
  implicit configDecorator: ConfigDecorator,
  partialRetriever: LocalPartialRetriever,
  ec: ExecutionContext,
  templateRenderer: TemplateRenderer)
    extends ActionRefiner[UserRequest, UserRequest] with ActionFunction[UserRequest, UserRequest] with I18nSupport {

  override protected def refine[A](request: UserRequest[A]): Future[Either[Result, UserRequest[A]]] =
    populatingUnreadMessageCount()(request).flatMap { messageCount =>
      if (!request.uri.contains("/signout")) {
        getPersonDetails()(request).map { a =>
          a.fold(
            Left(_),
            pd =>
              Right(
                UserRequest(
                  request.nino,
                  request.retrievedName,
                  request.saUserType,
                  request.credentials,
                  request.confidenceLevel,
                  pd,
                  request.trustedHelper,
                  request.profile,
                  messageCount,
                  request.activeTab,
                  request.breadcrumb,
                  request.request
                )
            )
          )
        }
      } else {
        Future.successful(
          Right(
            UserRequest(
              request.nino,
              request.retrievedName,
              request.saUserType,
              request.credentials,
              request.confidenceLevel,
              None,
              request.trustedHelper,
              request.profile,
              messageCount,
              request.activeTab,
              request.breadcrumb,
              request.request
            )
          )
        )
      }
    }

  def populatingUnreadMessageCount()(implicit request: UserRequest[_]): Future[Option[Int]] =
    if (configDecorator.personDetailsMessageCountEnabled) messageFrontendService.getUnreadMessageCount
    else Future.successful(None)

  private def getPersonDetails()(implicit request: UserRequest[_]): Future[Either[Result, Option[PersonDetails]]] = {

    implicit val hc: HeaderCarrier =
      HeaderCarrierConverter.fromRequestAndSession(request, request.session)

    request.nino match {
      case Some(nino) =>
        citizenDetailsService.personDetails(nino).map {
          case PersonDetailsSuccessResponse(pd) => Right(Some(pd))
          case PersonDetailsHiddenResponse =>
            Left(Locked(manualCorrespondenceView()))
          case _ => Right(None)
        }
      case _ => Future.successful(Right(None))
    }
  }

  override protected def executionContext: ExecutionContext = cc.executionContext
}
