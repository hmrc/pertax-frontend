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

import cats.data.EitherT
import com.google.inject.Inject
import models.tempAddressFix.{AddressFixRecord, AddressFixRecordRequest}
import play.api.mvc.{Action, AnyContent, MessagesControllerComponents}
import repositories.TempAddressFixRepository
import uk.gov.hmrc.internalauth.client.{AuthenticatedActionBuilder, BackendAuthComponents, IAAction, Resource, ResourceLocation, ResourceType}
import uk.gov.hmrc.internalauth.client.Predicate.Permission
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import play.api.libs.json.Json
import play.api.Logging
import services.CitizenDetailsService
import connectors.CitizenDetailsConnector
import uk.gov.hmrc.domain.Nino
import models.PersonDetails
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.{ExecutionContext, Future}

class FixController @Inject() (
  cc: MessagesControllerComponents,
  tempAddressFixRepository: TempAddressFixRepository,
  citizenDetailsService: CitizenDetailsService,
  citizenDetailsConnector: CitizenDetailsConnector,
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
    tempAddressFixRepository.findOneAndUpdate(nino, "processing", Some("todo")).flatMap {
      case None         =>
        logger.info(s"$nino was not found in the mongo collection")
        Future.successful(NotFound("No record found to fix"))
      case Some(record) =>
        logger.info(s"Fixing record for nino ${record.nino}")
        fixRecord(record).leftSemiflatMap { error =>
          tempAddressFixRepository.findOneAndUpdate(record.nino, "todo", Some("processing")).map {
            case Some(_) => InternalServerError(error.message)
            case None    =>
              logger.error(s"Cannot find record for nino $nino and status processing")
              InternalServerError("Something is seriously wrong. unexpected status in mongo")
          }
        }.merge
    }
  }

  private def fixRecord(
    record: AddressFixRecord
  )(implicit request: Request[_]): EitherT[Future, UpstreamErrorResponse, Result] =
    citizenDetailsService.personDetails(Nino(record.nino)).flatMap {
      case None          =>
        logger.error(s"Error nino ${record.nino} not found in citizen details")
        EitherT.rightT[Future, UpstreamErrorResponse](NotFound(s"nino ${record.nino} not found in citizen details"))
      case Some(details) =>
        fixAddress(record, details)
    }

  private def fixAddress(record: AddressFixRecord, details: PersonDetails)(implicit
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Result] =
    if (details.address.flatMap(_.country).contains("ABROAD - NOT KNOWN")) {
      logger.info(
        s"residential address for nino ${record.nino} is ABROAD - NOT KNOWN and need fixing"
      )
      val newAddress = details.address.map(_.copy(country = None, postcode = Some(record.postcode))).get
      citizenDetailsConnector.updateAddress(Nino(record.nino), details.etag, newAddress).semiflatMap { _ =>
        tempAddressFixRepository.findOneAndUpdate(record.nino, "done").map { newRecord =>
          Ok(Json.toJson(newRecord))
        }
      }
    } else if (details.correspondenceAddress.flatMap(_.country).contains("ABROAD - NOT KNOWN")) {
      EitherT.rightT[Future, UpstreamErrorResponse](NotImplemented("not done yet"))
    } else {
      logger.warn(
        s"Address is no longer ABROAD - NOT KNOWN for nino ${record.nino}, skipping record"
      )
      EitherT.liftF(tempAddressFixRepository.findOneAndUpdate(record.nino, "skipped", Some("processing")).map {
        case Some(newRecord) => Ok(Json.toJson(newRecord))
        case None            =>
          logger.error(s"Cannot find record for nino ${record.nino} and status processing")
          InternalServerError("Something is seriously wrong. unexpected status in mongo")
      })
    }

}
