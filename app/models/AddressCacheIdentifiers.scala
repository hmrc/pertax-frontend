/*
 * Copyright 2020 HM Revenue & Customs
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

package models

import controllers.bindable.AddrType
import models.addresslookup.{AddressRecord, RecordSet}
import models.dto.{AddressDto, AddressFinderDto, AddressPageVisitedDto, DateDto, InternationalAddressChoiceDto, ResidencyChoiceDto, TaxCreditsChoiceDto}

trait CacheIdentifier[A] {
  val id: String
}

case object AddressPageVisitedDtoId extends CacheIdentifier[AddressPageVisitedDto] {
  override val id: String = "addressPageVisitedDto"
}

case object SubmittedTaxCreditsChoiceId extends CacheIdentifier[TaxCreditsChoiceDto] {
  override val id: String = "taxCreditsChoiceDto"
}

case object SubmittedInternationalAddressChoiceId extends CacheIdentifier[InternationalAddressChoiceDto] {
  override val id: String = "internationalAddressChoiceDto"
}

abstract class AddressIdentifier[A](partialId: String) extends CacheIdentifier[A] {
  val typ: AddrType
  val id: String = s"$typ$partialId"
}

case class AddressFinderDtoId(typ: AddrType) extends AddressIdentifier[AddressFinderDto]("AddressFinderDto")

case class SelectedAddressRecordId(typ: AddrType) extends AddressIdentifier[AddressRecord]("SelectedAddressRecord")

case class SelectedRecordSetId(typ: AddrType) extends AddressIdentifier[RecordSet]("SelectedRecordSet")

case class SubmittedAddressDtoId(typ: AddrType) extends AddressIdentifier[AddressDto]("SubmittedAddressDto")

case class SubmittedStartDateId(typ: AddrType) extends AddressIdentifier[DateDto]("SubmittedStartDateDto")

case class SubmittedResidencyChoiceDtoId(typ: AddrType)
    extends AddressIdentifier[ResidencyChoiceDto]("ResidencyChoiceDto")
