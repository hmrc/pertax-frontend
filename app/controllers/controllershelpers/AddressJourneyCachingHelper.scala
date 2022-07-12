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

package controllers.controllershelpers

import com.google.inject.{Inject, Singleton}
import controllers.bindable.AddrType
import models._
import models.addresslookup.{AddressRecord, RecordSet}
import models.dto._
import play.api.Logging
import play.api.libs.json.Writes
import play.api.mvc.{Result, Results}
import services.LocalSessionCache
import uk.gov.hmrc.http.cache.client.{CacheMap, KeyStoreEntryValidationException}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class AddressJourneyCachingHelper @Inject() (val sessionCache: LocalSessionCache)(implicit ec: ExecutionContext)
    extends Results with Logging {

  val addressLookupServiceDownKey = "addressLookupServiceDown"

  def addToCache[A: Writes](id: CacheIdentifier[A], record: A)(implicit hc: HeaderCarrier): Future[CacheMap] =
    sessionCache.cache(id.id, record)

  def cacheAddressLookupServiceDown()(implicit hc: HeaderCarrier): Future[CacheMap] =
    sessionCache.cache(addressLookupServiceDownKey, true)

  def clearCache()(implicit hc: HeaderCarrier): Future[HttpResponse] =
    sessionCache.remove()

  def gettingCachedAddressPageVisitedDto[T](
    block: Option[AddressPageVisitedDto] => Future[T]
  )(implicit hc: HeaderCarrier): Future[T] =
    sessionCache.fetch() flatMap {
      case Some(cacheMap) =>
        block(cacheMap.getEntry[AddressPageVisitedDto](AddressPageVisitedDtoId.id))
      case None =>
        block(None)
    } recoverWith {
      case e: KeyStoreEntryValidationException =>
        logger.error(s"Failed to read cached address page visited")
        block(None)
      case NonFatal(e) => throw e
    }

  def gettingCachedAddressLookupServiceDown[T](block: Option[Boolean] => T)(implicit hc: HeaderCarrier): Future[T] =
    sessionCache.fetch() map { cacheMap =>
      block(cacheMap.flatMap(_.getEntry[Boolean](addressLookupServiceDownKey)))
    } recover {
      case e: KeyStoreEntryValidationException =>
        logger.error(s"Failed to read cached address lookup service down")
        block(None)
      case NonFatal(e) => throw e
    }

  def gettingCachedTaxCreditsChoiceDto[T](
    block: Option[TaxCreditsChoiceDto] => T
  )(implicit hc: HeaderCarrier): Future[T] =
    sessionCache.fetch() map { cacheMap =>
      block(cacheMap.flatMap(_.getEntry[TaxCreditsChoiceDto](SubmittedTaxCreditsChoiceId.id)))
    } recover {
      case e: KeyStoreEntryValidationException =>
        logger.error(s"Failed to read cached tax credits choice")
        block(None)
      case NonFatal(e) => throw e
    }

  def gettingCachedJourneyData[T](
    typ: AddrType
  )(block: AddressJourneyData => Future[T])(implicit hc: HeaderCarrier): Future[T] =
    sessionCache.fetch() flatMap {
      case Some(cacheMap) =>
        block(
          AddressJourneyData(
            cacheMap.getEntry[AddressPageVisitedDto](AddressPageVisitedDtoId.id),
            cacheMap.getEntry[ResidencyChoiceDto](SubmittedResidencyChoiceDtoId(typ).id),
            cacheMap.getEntry[RecordSet](SelectedRecordSetId(typ).id),
            cacheMap.getEntry[AddressFinderDto](AddressFinderDtoId(typ).id),
            cacheMap.getEntry[AddressRecord](SelectedAddressRecordId(typ).id),
            cacheMap.getEntry[AddressDto](SubmittedAddressDtoId(typ).id),
            cacheMap.getEntry[InternationalAddressChoiceDto](SubmittedInternationalAddressChoiceId.id),
            cacheMap.getEntry[DateDto](SubmittedStartDateId(typ).id),
            cacheMap.getEntry[Boolean](addressLookupServiceDownKey).getOrElse(false)
          )
        )
      case None =>
        block(AddressJourneyData(None, None, None, None, None, None, None, None, addressLookupServiceDown = false))
    } recoverWith {
      case e: KeyStoreEntryValidationException =>
        logger.error(s"Failed to read cached address")
        block(AddressJourneyData(None, None, None, None, None, None, None, None, addressLookupServiceDown = false))
      case NonFatal(e) => throw e
    }

  def enforceDisplayAddressPageVisited(
    addressPageVisitedDto: Option[AddressPageVisitedDto]
  )(block: => Future[Result])(implicit hc: HeaderCarrier): Future[Result] =
    addressPageVisitedDto match {
      case Some(_) =>
        block
      case None =>
        Future.successful(Redirect(controllers.address.routes.PersonalDetailsController.onPageLoad))
    }

}
