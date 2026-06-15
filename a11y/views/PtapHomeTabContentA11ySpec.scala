/*
 * Copyright 2026 HM Revenue & Customs
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

package views

import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import services.TabContentService
import testUtils.A11ySpec
import uk.gov.hmrc.scalatestaccessibilitylinter.domain.OutputFormat
import viewmodels.TabEnum

class PtapHomeTabContentA11ySpec extends A11ySpec {

  private lazy val ptapHomeTabContent                = app.injector.instanceOf[views.html.components.PtapHomeTabContent]
  private implicit lazy val messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  private implicit lazy val messages: Messages       = MessagesImpl(Lang("en"), messagesApi)

  private val tabContentService = new TabContentService

  private def fullPage(componentHtml: String): String =
    s"""<!doctype html>
       |<html lang="en">
       |  <head>
       |    <title>PTAP tab content accessibility test</title>
       |  </head>
       |  <body>
       |    <main>
       |      <h1>PTAP tab content accessibility test</h1>
       |      $componentHtml
       |    </main>
       |  </body>
       |</html>""".stripMargin

  "PtapHomeTabContent" must {
    "pass accessibility checks for Tasks and Activities card content" in {
      val containers =
        tabContentService.getTabContentModel(TabEnum.TASK.name, taskCount = 1).containers ++
          tabContentService.getTabContentModel(TabEnum.ACTIVITY.name, taskCount = 1).containers

      fullPage(ptapHomeTabContent(containers).toString) must passAccessibilityChecks(OutputFormat.Verbose)
    }
  }
}
