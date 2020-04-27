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

package controllers.address

import config.ConfigDecorator
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.controllershelpers.PersonalDetailsCardGenerator
import org.mockito.Mockito.reset
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.MessagesControllerComponents
import repositories.EditAddressLockRepository
import services.{AddressLookupService, AddressMovedService, CitizenDetailsService, LocalSessionCache}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.ExecutionContext

trait AddressSpecHelper extends BaseSpec with MockitoSugar with GuiceOneAppPerSuite {

  val mockAddressMovedService: AddressMovedService = mock[AddressMovedService]
  val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]
  val mockEditAddressLockRepository: EditAddressLockRepository = mock[EditAddressLockRepository]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockLocalSessionCache: LocalSessionCache = mock[LocalSessionCache]
  val mockAuthJourney: AuthJourney = mock[AuthJourney]
  val mockAddressLookupService: AddressLookupService = mock[AddressLookupService]
  val mockPersonalDetailsCardGenerator = mock[PersonalDetailsCardGenerator]

  lazy val withActiveTabAction: WithActiveTabAction = injected[WithActiveTabAction]
  lazy val mcc: MessagesControllerComponents = injected[MessagesControllerComponents]

  implicit lazy val partialRetriever: LocalPartialRetriever = injected[LocalPartialRetriever]
  implicit lazy val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit lazy val templateRenderer: TemplateRenderer = injected[TemplateRenderer]
  implicit lazy val executionContext: ExecutionContext = injected[ExecutionContext]

  override def afterEach: Unit =
    reset(
      mockAddressMovedService,
      mockCitizenDetailsService,
      mockEditAddressLockRepository,
      mockAuditConnector,
      mockLocalSessionCache,
      mockAuthJourney,
      mockAddressLookupService,
      mockPersonalDetailsCardGenerator
    )

  val nino: Nino = Fixtures.fakeNino
}
