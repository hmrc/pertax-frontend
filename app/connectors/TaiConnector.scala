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

package connectors

import cats.data.EitherT
import com.google.inject.name.Named
import com.google.inject.{Inject, Singleton}
import config.ConfigDecorator
import models.admin.TaxComponentsRetrievalToggle
import play.api.Logging
import play.api.libs.json.Format
import play.api.mvc.Request
import repositories.SessionCacheRepository
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps, UpstreamErrorResponse}
import uk.gov.hmrc.mongo.cache.DataKey
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait TaiConnector {
  def taxComponents[A](nino: Nino, year: Int)(format: Format[A])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Option[A]]
}

@Singleton
class DefaultTaiConnector @Inject() (
  val httpClientV2: HttpClientV2,
  servicesConfig: ServicesConfig,
  httpClientResponse: HttpClientResponse,
  configDecorator: ConfigDecorator,
  featureFlagService: FeatureFlagService
) extends TaiConnector
    with Logging {

  private lazy val taiUrl = servicesConfig.baseUrl("tai")

  override def taxComponents[A](nino: Nino, year: Int)(format: Format[A])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Option[A]] =
    featureFlagService.getAsEitherT(TaxComponentsRetrievalToggle).flatMap { toggle =>
      if (toggle.isEnabled) {
        val url = s"$taiUrl/tai/$nino/tax-account/$year/tax-components"
        httpClientResponse
          .read(
            httpClientV2
              .get(url"$url")
              .transform(_.withRequestTimeout(configDecorator.taiTimeoutInMilliseconds.milliseconds))
              .execute[Either[UpstreamErrorResponse, HttpResponse]](readEitherOf(readRaw), ec)
          )
          .map { resp =>
            resp.json.validate[A](format).asOpt.orElse {
              logger.error("Exception when parsing API response - returning None instead.")
              None
            }
          }
      } else {
        EitherT.rightT[Future, UpstreamErrorResponse](Option.empty[A])
      }
    }
}

@Singleton
class CachingTaiConnector @Inject() (
  @Named("default") underlying: TaiConnector,
  sessionCacheRepository: SessionCacheRepository
)(implicit ec: ExecutionContext)
    extends TaiConnector
    with Logging {

  private def cache[L, A](
    key: String
  )(
    f: => EitherT[Future, L, Option[A]]
  )(using request: Request[_], format: Format[A]): EitherT[Future, L, Option[A]] = {

    def fetchAndMaybeCache: EitherT[Future, L, Option[A]] =
      for {
        result <- f
        _      <- result match {
                    case Some(value) =>
                      EitherT.liftF(sessionCacheRepository.putSession[A](DataKey[A](key), value).map(_ => ()))
                    case None        =>
                      EitherT.rightT[Future, L](())
                  }
      } yield result

    EitherT(
      sessionCacheRepository
        .getFromSession[A](DataKey[A](key))
        .flatMap {
          case Some(value) => Future.successful(Right(Some(value)))
          case None        => fetchAndMaybeCache.value
        }
        .recover { case NonFatal(e) =>
          logger.warn(s"TaxComponents cache read failed for key=$key: ${e.getMessage}")
          Right(None)
        }
    )
  }

  override def taxComponents[A](nino: Nino, year: Int)(format: Format[A])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]
  ): EitherT[Future, UpstreamErrorResponse, Option[A]] = {
    val key = s"taxComponents.${nino.value}.$year"
    cache[UpstreamErrorResponse, A](key) {
      underlying.taxComponents(nino, year)(format)
    }(using request, format)
  }
}
