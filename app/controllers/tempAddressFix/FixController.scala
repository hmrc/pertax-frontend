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

package controllers.tempAddressFix

import com.google.inject.Inject
import models.tempAddressFix.{AddressFixRecord, AddressFixRecordRequest}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.TempAddressFixRepository
import uk.gov.hmrc.internalauth.client.{AuthenticatedActionBuilder, BackendAuthComponents, IAAction, Resource, ResourceLocation, ResourceType}
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import play.api.libs.json.Json
import play.api.Logging

import scala.concurrent.ExecutionContext

class FixController @Inject() (
  cc: MessagesControllerComponents,
  fixControllerHelper: FixControllerHelper,
  tempAddressFixRepository: TempAddressFixRepository,
  internalAuth: BackendAuthComponents
)(implicit ec: ExecutionContext)
    extends FrontendController(cc)
    with Logging {

  private val permission: Permission =
    Permission(
      resource = Resource(
        resourceType = ResourceType("ddcn-live-admin-frontend"),
        resourceLocation = ResourceLocation("*")
      ),
      action = IAAction("ADMIN")
    )

  private def auth(): AuthenticatedActionBuilder[Unit, AnyContent] =
    internalAuth.authorizedAction(permission)

  def bulkInsert: Action[AnyContent] = auth().async { implicit request =>
    val records = request.body.asJson.fold(Seq.empty)(_.as[Seq[AddressFixRecordRequest]])
    logger.info(s"Inserting ${records.size} in tempAddressFixRepository")

    tempAddressFixRepository
      .insertMany(records.map(_.toAddresRecord))
      .map { inserted =>
        Ok(
          Json.obj(
            "received" -> records.size,
            "inserted" -> inserted
          )
        )
      }
  }

  def getData(key: String): Action[AnyContent] = auth().async { implicit request =>
    tempAddressFixRepository
      .findByKey(key)
      .map(r => Ok(r.toString))
  }

  def getAllData: Action[AnyContent] = auth().async { implicit request =>
    tempAddressFixRepository.findAll
      .map(r => Ok(Json.toJson(r).toString))
  }

  def getFixARecord(nino: String): Action[AnyContent] = auth().async { implicit request =>
    fixControllerHelper.processRecord(nino)
  }

}
