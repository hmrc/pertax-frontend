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

package controllers.helpers

import controllers.bindable.AddrType
import controllers.{AddressController, routes}
import models.addresslookup.AddressRecord
import models.dto._
import models.{AddressJourneyData, PertaxContext}
import play.api.mvc.Result
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future


trait AddressJourneyCachingHelper { this: AddressController =>

  def cacheAddressFinderDto(typ: AddrType, addressFinderDto: AddressFinderDto)(implicit hc: HeaderCarrier): Future[CacheMap] =
    sessionCache.cache(s"${typ}AddressFinderDto", addressFinderDto)

  def cacheSelectedAddressRecord(typ: AddrType, addressRecord: AddressRecord)(implicit hc: HeaderCarrier): Future[CacheMap] =
    sessionCache.cache(s"${typ}SelectedAddressRecord", addressRecord)

  def cacheSubmittedAddressDto(typ: AddrType, addressDto: AddressDto)(implicit hc: HeaderCarrier): Future[CacheMap] =
    sessionCache.cache(s"${typ}SubmittedAddressDto", addressDto)

  def cacheSubmittedStartDate(typ: AddrType, startDate: DateDto)(implicit hc: HeaderCarrier): Future[CacheMap] =
    sessionCache.cache(s"${typ}SubmittedStartDateDto", startDate)

  def cacheSubmitedResidencyChoiceDto(residencyChoiceDto: ResidencyChoiceDto)(implicit hc: HeaderCarrier): Future[CacheMap] =
    sessionCache.cache(s"${residencyChoiceDto.residencyChoice}ResidencyChoiceDto", residencyChoiceDto)

  def cacheAddressPageVisited(hasVisited: AddressPageVisitedDto)(implicit hc: HeaderCarrier): Future[CacheMap] =
  sessionCache.cache("addressPageVisitedDto", hasVisited)

  def cacheSubmitedTaxCreditsChoiceDto(taxCreditsChoice: TaxCreditsChoiceDto)(implicit hc: HeaderCarrier): Future[CacheMap] =
    sessionCache.cache("taxCreditsChoiceDto", taxCreditsChoice)

  def cacheAddressLookupServiceDown()(implicit  hc: HeaderCarrier): Future[CacheMap] =
    sessionCache.cache("addressLookupServiceDown", true)

  def clearCache()(implicit hc: HeaderCarrier): Unit =
    sessionCache.remove()

  //This is needed beacuse there is no AddrType available to call gettingCachedJourneyData
  def gettingCachedAddressPageVisitedDto[T](block: Option[AddressPageVisitedDto] => Future[T])(implicit hc: HeaderCarrier, context: PertaxContext): Future[T] = {
    sessionCache.fetch() flatMap  {
      case Some(cacheMap) =>
        block(cacheMap.getEntry[AddressPageVisitedDto]("addressPageVisitedDto"))
      case None =>
        block(None)
    }
  }

  def gettingCachedTaxCreditsChoiceDto[T](block: Option[TaxCreditsChoiceDto] => T)(implicit hc: HeaderCarrier, context: PertaxContext): Future[T] = {
    sessionCache.fetch() map { cacheMap => {
        block(cacheMap.flatMap(_.getEntry[TaxCreditsChoiceDto]("taxCreditsChoiceDto")))
      }
    }
  }

  def gettingCachedJourneyData[T](typ: AddrType)(block: AddressJourneyData => Future[T])(implicit hc: HeaderCarrier, context: PertaxContext): Future[T] = {
    sessionCache.fetch() flatMap {
      case Some(cacheMap) =>
        block(AddressJourneyData(cacheMap.getEntry[AddressPageVisitedDto]("addressPageVisitedDto"),
              cacheMap.getEntry[ResidencyChoiceDto](s"${typ}ResidencyChoiceDto"),
              cacheMap.getEntry[AddressFinderDto](s"${typ}AddressFinderDto"),
              cacheMap.getEntry[AddressRecord](s"${typ}SelectedAddressRecord"),
              cacheMap.getEntry[AddressDto](s"${typ}SubmittedAddressDto"),
              cacheMap.getEntry[DateDto](s"${typ}SubmittedStartDateDto"),
              cacheMap.getEntry[Boolean]("addressLookupServiceDown").getOrElse(false)))
      case None =>
        block(AddressJourneyData(None, None, None, None, None, None, false))
    }
  }


  def enforceDisplayAddressPageVisited(addressPageVisitedDto: Option[AddressPageVisitedDto])(block: => Future[Result])(implicit hc: HeaderCarrier, context: PertaxContext): Future[Result] = {
    addressPageVisitedDto match {
      case Some(_) =>
        block
      case None =>
        Future.successful(Redirect(routes.AddressController.personalDetails()))
    }
  }

  def enforceResidencyChoiceSubmitted(journeyData: AddressJourneyData)(block: AddressJourneyData => Future[Result]): Future[Result] = {
    journeyData match {
      case AddressJourneyData(_, Some(_), _, _, _, _ , _) =>
        block(journeyData)
      case AddressJourneyData(_, None, _, _, _, _, _) =>
        Future.successful(Redirect(routes.AddressController.personalDetails))
    }
  }

}
