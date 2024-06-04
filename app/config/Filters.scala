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

package config

import org.apache.pekko.stream.Materializer
import play.api.http.{EnabledFilters, HttpFilters}
import play.api.mvc.{EssentialFilter, RequestHeader, Result}
import uk.gov.hmrc.sca.connectors.ScaWrapperDataConnector
import uk.gov.hmrc.sca.filters.WrapperDataFilter

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

class SCAWrapperDataFilter @Inject() (
  scaWrapperDataConnector: ScaWrapperDataConnector
)(implicit val ec: ExecutionContext, override val mat: Materializer)
    extends WrapperDataFilter(scaWrapperDataConnector)(ec, mat) {

  override def apply(f: RequestHeader => Future[Result])(rh: RequestHeader): Future[Result] =
    super.apply(f)(rh)
}

@Singleton
class Filters @Inject() (
  defaultFilters: EnabledFilters,
  wrapperDataFilter: SCAWrapperDataFilter
) extends HttpFilters {

  override val filters: Seq[EssentialFilter] = {

    defaultFilters.filters ++ Some(wrapperDataFilter)
  }
}
