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

package modules

import com.google.inject.AbstractModule
import config.LocalTemplateRenderer
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.audit.DefaultAuditConnector
import uk.gov.hmrc.play.bootstrap.auth.DefaultAuthConnector
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, HttpClient}
import uk.gov.hmrc.renderer.TemplateRenderer

class LocalGuiceModule extends AbstractModule {
  override def configure() = {
    bind(classOf[TemplateRenderer]).to(classOf[LocalTemplateRenderer])
    bind(classOf[HttpClient]).to(classOf[DefaultHttpClient])
    bind(classOf[AuthConnector]).to(classOf[DefaultAuthConnector])
    bind(classOf[AuditConnector]).to(classOf[DefaultAuditConnector])
  }
}
