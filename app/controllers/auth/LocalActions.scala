/*
 * Copyright 2017 HM Revenue & Customs
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

import controllers.PertaxBaseController
import models._
import play.api.Logger
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, Request, Result}
import services._
import uk.gov.hmrc.play.frontend.auth.connectors.domain.{PayeAccount, SaAccount}
import uk.gov.hmrc.play.frontend.auth.{AllowAll, AuthContext}

import scala.concurrent.Future

trait LocalActions extends ConfidenceLevelAndCredentialStrengthChecker { this: PertaxBaseController =>

  def citizenDetailsService: CitizenDetailsService
  def userDetailsService: UserDetailsService
  def pertaxRegime: PertaxRegime

  def ProtectedAction(breadcrumb: Breadcrumb, fetchPersonDetails: Boolean = true)(block: PertaxContext => Future[Result]): Action[AnyContent] = {
    AuthorisedFor(pertaxRegime, pageVisibility = AllowAll).async {
      implicit authContext => implicit request =>
        createPertaxContextAndExecute(fetchPersonDetails) { implicit pertaxContext =>
          withBreadcrumb(breadcrumb) { implicit pertaxContext =>
            enforceMinimumUserProfile {
              block(pertaxContext)
            }
          }
        }
    }
  }

  def AuthorisedAction(fetchPersonDetails: Boolean = true)(block: PertaxContext => Future[Result]): Action[AnyContent] = {
    AuthorisedFor(pertaxRegime, pageVisibility = AllowAll).async {
      implicit authContext => implicit request =>
        createPertaxContextAndExecute(fetchPersonDetails) { implicit pertaxContext =>
          block(pertaxContext)
        }
    }
  }

  def withBreadcrumb(breadcrumb: Breadcrumb)(block: PertaxContext => Future[Result])(implicit pertaxContext: PertaxContext) =
    block(pertaxContext.withBreadcrumb(Some(breadcrumb)))

  def renderInternalServerError(pertaxContext: PertaxContext) = views.html.error(
    "global.error.InternalServerError500.title",
    Some("global.error.InternalServerError500.heading"),
    Some("global.error.InternalServerError500.message")
  )(pertaxContext, implicitly[Messages])

  def createPertaxContextAndExecute(fetchPersonDetails: Boolean)(block: PertaxContext => Future[Result])(implicit authContext: AuthContext, request: Request[AnyContent]): Future[Result] = {

    def withUserDetails(block: UserDetails => Future[Result]): Future[Result] = {

      implicit val context = PertaxContext(request, partialRetriever, configDecorator, None)  //FIXME, can we supply a PertaxUser to this?

      authContext.userDetailsUri map { uri =>
        userDetailsService.getUserDetails(uri) flatMap {
          case Some(userDetails) => block(userDetails)
          case None =>
            Future.successful(InternalServerError( renderInternalServerError(context) ))
        }
      } getOrElse {
        Logger.error("There was no user-details URI for current user")
        Future.successful(InternalServerError( renderInternalServerError(context) ))
      }
    }

    //If the user is from GG, determine if they have the right conditions to be a highly privileged user
    def isHighGovernmentGatewayUser(isGovernmentGateway: Boolean): Boolean = {
      isGovernmentGateway && userHasHighConfidenceLevelAndStrongCredentials
    }

    withUserDetails { userDetails =>

      val isHigh = isHighGovernmentGatewayUser(userDetails.hasGovernmentGatewayAuthProvider)

      val pertaxUser = PertaxUser(authContext, userDetails, None, isHigh)
      implicit val context = PertaxContext(request, partialRetriever, configDecorator, Some(pertaxUser))

      if (fetchPersonDetails)
        withPersonDetailsIfPaye { personDetails =>
          block(context.withUser(Some(pertaxUser.withPersonDetails(personDetails))))
        }
      else
        block(context.withUser(Some(pertaxUser)))
    }

  }

  def enforceMinimumUserProfile(block: => Future[Result])(implicit pertaxContext: PertaxContext): Future[Result] = {
    pertaxContext.user.filter(user => user.isHighGovernmentGatewayOrVerify || (configDecorator.allowSaPreview && user.isSa)).map {
      user => block
    } getOrElse {
      Future.successful( Redirect(controllers.routes.ApplicationController.uplift(redirectUrl = Some(pertaxContext.request.uri))) )
    }
  }

  private def withPersonDetailsIfPaye(block: Option[PersonDetails] => Future[Result])(implicit pertaxContext: PertaxContext): Future[Result] = {
    pertaxContext.userAndNino match {
      case Some( (user, nino) ) if user.isHighGovernmentGatewayOrVerify =>
        citizenDetailsService.personDetails(nino) flatMap {
          case PersonDetailsSuccessResponse(pd) => block(Some(pd))
          case PersonDetailsHiddenResponse =>
            Future.successful(Locked(views.html.manualCorrespondence()))
          case _ => block(None)
        }
      case _ => block(None)
    }
  }

  def enforcePersonDetails(block: => Future[Result])(implicit pertaxContext: PertaxContext): Future[Result] = {
    enforcePersonDetails {
      payeAccount => personDetails =>
        block
    }
  }

  def enforcePersonDetails(block: PayeAccount => PersonDetails => Future[Result])(implicit pertaxContext: PertaxContext): Future[Result] = {

    enforcePayeAccount { payeAccount =>
      pertaxContext.user.flatMap {
        _.personDetails map { personDetails =>
          block(payeAccount)(personDetails)
        }
      } getOrElse {
        Logger.warn("User had no PersonDetails in context when one was required")
        Future.successful(Unauthorized( renderInternalServerError(pertaxContext) ))
      }
    }
  }

  private def enforcePayeAccount(block: PayeAccount => Future[Result])(implicit pertaxContext: PertaxContext): Future[Result] = {

    pertaxContext.user.flatMap {
      _.authContext.principal.accounts.paye.map { payeAccount =>
        block(payeAccount)
      }
    } getOrElse {
      Logger.warn("User had no paye account when one was required")
      Future.successful(Unauthorized( renderInternalServerError(pertaxContext) ))
    }
  }

  def enforceSaAccount(block: SaAccount => Future[Result])(implicit pertaxContext: PertaxContext): Future[Result] = {

    pertaxContext.user.flatMap {
      _.authContext.principal.accounts.sa.map { saAccount =>
        block(saAccount)
      }
    } getOrElse {
      Logger.warn("User had no sa account when one was required")
      Future.successful(Unauthorized( renderInternalServerError(pertaxContext) ))
    }
  }

  def enforceGovernmentGatewayUser(block: => Future[Result])(implicit pertaxContext: PertaxContext) =
    PertaxUser.ifGovernmentGatewayUser(block) getOrElse Future.successful(Unauthorized( renderInternalServerError(pertaxContext) ))

  def enforceHighGovernmentGatewayUser(block: => Future[Result])(implicit pertaxContext: PertaxContext) =
    PertaxUser.ifHighGovernmentGatewayUser(block) getOrElse Future.successful(Unauthorized( renderInternalServerError(pertaxContext) ))

  def enforceLowGovernmentGatewayUser(block: => Future[Result])(implicit pertaxContext: PertaxContext) =
    PertaxUser.ifLowGovernmentGatewayUser(block) getOrElse Future.successful(Unauthorized( renderInternalServerError(pertaxContext) ))
  
  def enforceVerifyUser(block: => Future[Result])(implicit pertaxContext: PertaxContext) =
    PertaxUser.ifVerifyUser(block) getOrElse Future.successful(Unauthorized( renderInternalServerError(pertaxContext) ))

  def enforceHighGovernmentGatewayUserOrVerifyUser(block: => Future[Result])(implicit pertaxContext: PertaxContext) =
    PertaxUser.ifHighGovernmentGatewayOrVerifyUser(block) getOrElse Future.successful(Unauthorized( renderInternalServerError(pertaxContext) ))

  def enforcePayeUser(block: => Future[Result])(implicit pertaxContext: PertaxContext) =
    PertaxUser.ifPayeUser(block) getOrElse Future.successful(Unauthorized( renderInternalServerError(pertaxContext) ))

  def enforceSaUser(block: => Future[Result])(implicit pertaxContext: PertaxContext) =
    PertaxUser.ifSaUser(block) getOrElse Future.successful(Unauthorized( renderInternalServerError(pertaxContext) ))
}
