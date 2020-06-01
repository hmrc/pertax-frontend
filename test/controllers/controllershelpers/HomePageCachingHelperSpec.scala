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

package controllers.controllershelpers

import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsBoolean
import services.SafeLocalSessionCache
import uk.gov.hmrc.http.cache.client.CacheMap
import util.{BaseSpec, Fixtures}

import scala.concurrent.{ExecutionContext, Future}

class HomePageCachingHelperSpec extends BaseSpec with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  implicit val ec = app.injector.instanceOf[ExecutionContext]

  val safeLocalSessionCache = mock[SafeLocalSessionCache]
  val nino = Some(Fixtures.fakeNino)
  val homePageCachingHelper = new HomePageCachingHelper(safeLocalSessionCache)

  override def beforeEach(): Unit =
    reset(safeLocalSessionCache)

  "Calling HomePageCachingHelper.hasUserDismissedUrInvitation" should {
    "return true if cached value returns true" in {
      when(safeLocalSessionCache.fetch(meq(nino))(any(), any())).thenReturn(
        Future.successful(Some(CacheMap("id", Map("urBannerDismissed" -> JsBoolean(true)))))
      )

      val result = homePageCachingHelper.hasUserDismissedUrInvitation(nino)

      result.futureValue shouldBe true

      verify(safeLocalSessionCache, times(1)).fetch(meq(nino))(any(), any())
    }

    "return false if cached value returns false" in {
      when(safeLocalSessionCache.fetch(meq(nino))(any(), any())).thenReturn(
        Future.successful(Some(CacheMap("id", Map("urBannerDismissed" -> JsBoolean(false)))))
      )

      val result = homePageCachingHelper.hasUserDismissedUrInvitation(nino)

      result.futureValue shouldBe false

      verify(safeLocalSessionCache, times(1)).fetch(meq(nino))(any(), any())
    }

    "return false if cache returns no record" in {
      when(safeLocalSessionCache.fetch(meq(nino))(any(), any())).thenReturn(
        Future.successful(None)
      )

      val result = homePageCachingHelper.hasUserDismissedUrInvitation(nino)

      result.futureValue shouldBe false

      verify(safeLocalSessionCache, times(1)).fetch(meq(nino))(any(), any())
    }
  }

  "Calling HomePageCachingHelper.StoreUserUrDismissal" should {
    "Store true in session cache" in {
      val cacheMap = CacheMap(("id"), Map.empty)

      when(safeLocalSessionCache.cache(meq(nino), any(), any())(any(), any(), any())).thenReturn(
        Future.successful(cacheMap)
      )

      val result = homePageCachingHelper.storeUserUrDismissal(nino)
      result.futureValue shouldBe cacheMap

      verify(safeLocalSessionCache, times(1)).cache(meq(nino), meq("urBannerDismissed"), meq(true))(any(), any(), any())
    }
  }
}
