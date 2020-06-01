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

package services

import java.util.UUID

import org.mockito.{ArgumentMatcher, Matchers}
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.{reset, times, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Writes._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import util.{BaseSpec, Fixtures}

import scala.concurrent.ExecutionContext

class SafeLocalSessionCacheSpec extends BaseSpec with MockitoSugar with BeforeAndAfterEach with ScalaFutures {
  implicit val ec = app.injector.instanceOf[ExecutionContext]

  override implicit val hc = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID()}")))
  lazy val safeHc = hc.copy(sessionId = Some(SessionId(nino.nino)))

  val localSessionCache = mock[LocalSessionCache]
  val nino = Fixtures.fakeNino

  val safeLocalSessionCache = new SafeLocalSessionCache(localSessionCache)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(localSessionCache)
  }

  case class containNinoAsSessionId(nino: Nino) extends ArgumentMatcher[HeaderCarrier] {
    def matches(request: Any): Boolean =
      request match {
        case hc: HeaderCarrier if (hc.sessionId == Some(SessionId(nino.nino))) => true
        case _                                                                 => false
      }
  }

  "cache" should {
    val formId = "form-id"

    "call through to local session cache when there is a session id" in {
      safeLocalSessionCache.cache(Some(nino), formId, true)

      verify(localSessionCache, times(1)).cache(meq(formId), meq(true))(any(), meq(hc), any())
    }

    "replaces the session id with a nino when there is no session id" in {
      implicit val hc = HeaderCarrier()

      safeLocalSessionCache.cache(Some(nino), formId, true)

      verify(localSessionCache, times(1))
        .cache(meq(formId), meq(true))(any(), Matchers.argThat(containNinoAsSessionId(nino)), any())
    }

    "throws if there is no Nino" in {
      the[RuntimeException] thrownBy {
        safeLocalSessionCache.cache(None, formId, true)
      } should have message "No NINO found"
    }
  }

  "fetch" should {
    "call through to local session cache when there is a session id" in {
      safeLocalSessionCache.fetch(Some(nino))
      verify(localSessionCache, times(1)).fetch()(meq(hc), any())
    }

    "replaces the session id with a nino when there is no session id" in {
      implicit val hc = HeaderCarrier()

      safeLocalSessionCache.fetch(Some(nino))

      verify(localSessionCache, times(1))
        .fetch()(Matchers.argThat(containNinoAsSessionId(nino)), any())
    }
  }

  "fetchAndGetEntry" should {
    val key = "key"

    "call through to local session cache when there is a session id" in {
      safeLocalSessionCache.fetchAndGetEntry[Boolean](Some(nino), key)
      verify(localSessionCache, times(1)).fetchAndGetEntry(meq(key))(meq(hc), any(), any())
    }

    "replaces the session id with a nino when there is no session id" in {
      implicit val hc = HeaderCarrier()

      safeLocalSessionCache.fetchAndGetEntry[Boolean](Some(nino), key)

      verify(localSessionCache, times(1))
        .fetchAndGetEntry(meq(key))(Matchers.argThat(containNinoAsSessionId(nino)), any(), any())
    }
  }

  "remove" should {
    "call through to local session cache when there is a session id" in {
      safeLocalSessionCache.remove(Some(nino))
      verify(localSessionCache, times(1)).remove()(meq(hc), any())
    }

    "replaces the session id with a nino when there is no session id" in {
      implicit val hc = HeaderCarrier()

      safeLocalSessionCache.remove(Some(nino))

      verify(localSessionCache, times(1))
        .remove()(Matchers.argThat(containNinoAsSessionId(nino)), any())
    }
  }
}
