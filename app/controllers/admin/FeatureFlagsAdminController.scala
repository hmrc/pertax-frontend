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

package controllers.admin

import controllers.auth.InternalAuthAction
import models.admin.{FeatureFlag, FeatureFlagName}
import play.api.libs.json.{JsBoolean, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import services.admin.FeatureFlagService
import uk.gov.hmrc.internalauth.client.{AuthenticatedRequest, Retrieval}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import util.AuditServiceTools

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class FeatureFlagsAdminController @Inject() (
  val auth: InternalAuthAction,
  featureFlagService: FeatureFlagService,
  cc: ControllerComponents,
  auditConnector: AuditConnector
)(implicit ec: ExecutionContext)
    extends AbstractController(cc) {

  def get: Action[AnyContent] = auth().async {
    featureFlagService.getAll
      .map(flags => Ok(Json.toJson(flags)))
  }

  def put(flagName: FeatureFlagName): Action[AnyContent] = auth().async { request =>
    val hc = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    request.body.asJson match {
      case Some(JsBoolean(enabled)) =>
        featureFlagService
          .set(flagName, enabled)
          .map { _ =>
            auditConnector.sendEvent(
              AuditServiceTools.buildAdminEvent(
                "adminEvent",
                "changedToggleState",
                Map(flagName.toString -> Some(enabled.toString))
              )(hc, request)
            )
            NoContent
          }
      case _                        =>
        Future.successful(BadRequest)
    }
  }

  def putAll: Action[AnyContent] = auth().async { request =>
    val flags = request.body.asJson.map(_.as[Seq[FeatureFlag]]).getOrElse(Seq.empty)
    Future
      .sequence(flags.map { flag =>
        featureFlagService.set(flag.name, flag.isEnabled)
      })
      .map(_.foldLeft(NoContent) { (acc, value) =>
        if (acc.header.status == INTERNAL_SERVER_ERROR)
          acc
        else {
          if (value) NoContent else InternalServerError("Error while setting flags")
        }
      })
  }
}
