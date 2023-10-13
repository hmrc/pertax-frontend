/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.JsBoolean
import services.LocalSessionCache
import testUtils.BaseSpec
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.Future

class HomePageCachingHelperSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(bind[LocalSessionCache].toInstance(mock[LocalSessionCache]))
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(inject[LocalSessionCache])
  }

  "Calling HomePageCachingHelper.hasUserDismissedUrInvitation" must {
    trait LocalSetup {

      def urBannerDismissedValueInSessionCache: Option[Boolean]

      lazy val cachingHelper: HomePageCachingHelper = {

        val c = inject[HomePageCachingHelper]

        when(inject[LocalSessionCache].fetch()(any(), any())) thenReturn {

          Future.successful {
            urBannerDismissedValueInSessionCache.map { myBool =>
              CacheMap("id", Map("urBannerDismissed" -> JsBoolean(myBool)))
            }
          }
        }
        c
      }

      lazy val hasUserDismissedUrInvitationResult: Boolean = cachingHelper.hasUserDismissedBanner.futureValue
    }

    "return true if cached value returns true" in new LocalSetup {
      lazy val urBannerDismissedValueInSessionCache: Option[Boolean] = Some(true)

      hasUserDismissedUrInvitationResult mustBe true

      verify(cachingHelper.sessionCache, times(1)).fetch()(any(), any())
    }

    "return false if cached value returns false" in new LocalSetup {
      lazy val urBannerDismissedValueInSessionCache: Option[Boolean] = Some(false)

      hasUserDismissedUrInvitationResult mustBe false

      verify(cachingHelper.sessionCache, times(1)).fetch()(any(), any())
    }

    "return false if cache returns no record" in new LocalSetup {
      lazy val urBannerDismissedValueInSessionCache: Option[Nothing] = None

      hasUserDismissedUrInvitationResult mustBe false

      verify(cachingHelper.sessionCache, times(1)).fetch()(any(), any())
    }
  }

  "Calling HomePageCachingHelper.StoreUserUrDismissal" must {

    trait LocalSetup {

      lazy val cachingHelper: HomePageCachingHelper = {

        val c = inject[HomePageCachingHelper]

        when(inject[LocalSessionCache].cache(any(), any())(any(), any(), any())) thenReturn {
          Future.successful(CacheMap("id", Map.empty))
        }
        c
      }
    }

    "Store true in session cache" in new LocalSetup {

      val result: CacheMap = cachingHelper.storeUserUrDismissal().futureValue

      result mustBe CacheMap("id", Map.empty)
      verify(cachingHelper.sessionCache, times(1)).cache(meq("urBannerDismissed"), meq(true))(any(), any(), any())
    }
  }

}
