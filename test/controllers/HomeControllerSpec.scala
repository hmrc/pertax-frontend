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

package controllers

import controllers.auth.AuthJourney
import controllers.controllershelpers.{HomeCardGenerator, HomePageCachingHelper, PaperlessInterruptHelper, RlsInterruptHelper}
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models.{TaxComponents, TaxComponentsAvailableState}
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import testUtils.fakes.{FakeAuthJourney, FakePaperlessInterruptHelper, FakeRlsInterruptHelper}
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.HeaderNames
import util.AlertBannerHelper

import scala.concurrent.Future

class HomeControllerSpec extends BaseSpec with WireMockHelper {
  val fakeAuthJourney              = new FakeAuthJourney
  val fakeRlsInterruptHelper       = new FakeRlsInterruptHelper
  val fakePaperlessInterruptHelper = new FakePaperlessInterruptHelper

  val mockTaiService: TaiService                       = mock[TaiService]
  val mockBreathingSpaceService: BreathingSpaceService = mock[BreathingSpaceService]
  val mockHomeCardGenerator: HomeCardGenerator         = mock[HomeCardGenerator]
  val mockHomePageCachingHelper: HomePageCachingHelper = mock[HomePageCachingHelper]
  val mockSeissService: SeissService                   = mock[SeissService]
  val mockAlertBannerHelper: AlertBannerHelper         = mock[AlertBannerHelper]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthJourney].toInstance(fakeAuthJourney),
      bind[RlsInterruptHelper].toInstance(fakeRlsInterruptHelper),
      bind[PaperlessInterruptHelper].toInstance(fakePaperlessInterruptHelper),
      bind[TaiService].toInstance(mockTaiService),
      bind[BreathingSpaceService].toInstance(mockBreathingSpaceService),
      bind[HomeCardGenerator].toInstance(mockHomeCardGenerator),
      bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
      bind[SeissService].toInstance(mockSeissService),
      bind[AlertBannerHelper].toInstance(mockAlertBannerHelper)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    org.mockito.MockitoSugar.reset(
      mockTaiService,
      mockBreathingSpaceService,
      mockHomeCardGenerator,
      mockHomePageCachingHelper,
      mockSeissService,
      mockAlertBannerHelper
    )

    when(mockSeissService.hasClaims(any())(any())).thenReturn(Future.successful(true))
    when(mockBreathingSpaceService.getBreathingSpaceIndicator(any())(any(), any()))
      .thenReturn(Future.successful(WithinPeriod))
    when(mockHomeCardGenerator.getIncomeCards(any())(any(), any())).thenReturn(
      Future.successful(Seq.empty)
    )
    when(mockHomeCardGenerator.getPensionCards()(any())).thenReturn(
      Future.successful(List.empty)
    )
    when(mockHomeCardGenerator.getBenefitCards(any(), any())(any())).thenReturn(
      List.empty
    )
    when(mockAlertBannerHelper.getContent(any(), any(), any())).thenReturn(
      Future.successful(List.empty)
    )
    when(mockTaiService.retrieveTaxComponentsState(any(), any())(any())).thenReturn(
      Future.successful(TaxComponentsAvailableState(TaxComponents(List("MarriageAllowanceReceived"))))
    )

  }

  def currentRequest[A]: Request[A] =
    FakeRequest()
      .withSession(HeaderNames.xSessionId -> "FAKE_SESSION_ID")
      .asInstanceOf[Request[A]]

  "Calling HomeController.index" must {

    "return a 200 status when accessing index page" in {

      val expectedOutput =
        """
          |<!DOCTYPE html>
          |<html lang="en" class="govuk-template ">
          |  <head>
          |    <meta charset="utf-8">
          |    <title>Account home - Personal tax account - GOV.UK</title>
          |    <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
          |    <meta name="theme-color" content="#0b0c0c">
          |    <meta http-equiv="X-UA-Compatible" content="IE=edge">
          |      <link rel="shortcut icon" sizes="16x16 32x32 48x48" href="/personal-account/hmrc-frontend/assets/govuk/images/favicon.ico" type="image/x-icon">
          |      <link rel="mask-icon" href="/personal-account/hmrc-frontend/assets/govuk/images/govuk-mask-icon.svg" color="#0b0c0c">
          |      <link rel="apple-touch-icon" sizes="180x180" href="/personal-account/hmrc-frontend/assets/govuk/images/govuk-apple-touch-icon-180x180.png">
          |      <link rel="apple-touch-icon" sizes="167x167" href="/personal-account/hmrc-frontend/assets/govuk/images/govuk-apple-touch-icon-167x167.png">
          |      <link rel="apple-touch-icon" sizes="152x152" href="/personal-account/hmrc-frontend/assets/govuk/images/govuk-apple-touch-icon-152x152.png">
          |      <link rel="apple-touch-icon" href="/personal-account/hmrc-frontend/assets/govuk/images/govuk-apple-touch-icon.png">
          |  <!--[if !IE]>-->
          |  <script
          |    src="http://localhost:12345/tracking-consent/tracking.js"
          |    id="tracking-consent-script-tag"
          |    data-gtm-container="c"
          |    data-language="en"
          |  ></script>
          |
          |  <!--<![endif]-->
          |
          |<!--[if lte IE 8]>
          |<script src="/personal-account/hmrc-frontend/assets/vendor/html5shiv.min.js"></script>
          |<link href="/personal-account/hmrc-frontend/assets/hmrc-frontend-ie8-5.66.0.min.css" media="all" rel="stylesheet" type="text/css" />
          |<![endif]-->
          |<!--[if gt IE 8]><!-->
          |<link href="/personal-account/hmrc-frontend/assets/hmrc-frontend-5.66.0.min.css"  media="all" rel="stylesheet" type="text/css" />
          |<!--<![endif]-->
          |
          |    <meta name="format-detection" content="telephone=no" />
          |    <link  rel="stylesheet" href='/personal-account/assets/stylesheets/pertaxMain.css'/>
          |    <link href="/personal-account/hmrc-frontend/assets/accessible-autocomplete-5.66.0.css"  media="all" rel="stylesheet" type="text/css" />
          |
          |        <meta name="hmrc-timeout-dialog"
          |content="hmrc-timeout-dialog"
          |data-language="en"
          |data-timeout="900"
          |data-countdown="120"
          |data-keep-alive-url="/personal-account/keep-alive"
          |data-sign-out-url="/personal-account/signout?continueUrl=http%3A%2F%2Flocalhost%3A9514%2Ffeedback%2FPERTAX"
          |data-timeout-url="/personal-account/timeout"
          |data-title=""
          |data-message=""
          |data-message-suffix=""
          |data-keep-alive-button-text=""
          |data-sign-out-button-text=""
          |data-synchronise-tabs="true"
          |data-hide-sign-out-button=""
          |/>
          |
          |    <link href="/personal-account/sca-wrapper/assets/pta.css" media="all" rel="stylesheet" type="text/css"  />
          |    <meta property="og:image" content="/personal-account/hmrc-frontend/assets/govuk/images/govuk-opengraph-image.png">
          |  </head>
          |  <body class="govuk-template__body ">
          |    <script >document.body.className = ((document.body.className) ? document.body.className + ' js-enabled' : 'js-enabled');</script>
          |      <a href="#main-content" class="govuk-skip-link" data-module="govuk-skip-link">Skip to main content</a>
          |
          |<header role="banner">
          |    <div class="govuk-header hmrc-header " data-module="govuk-header"
          |            >
          |      <div class="govuk-header__container  govuk-width-container">
          |        <div class="govuk-header__logo">
          |          <a href="https://www.gov.uk" class="govuk-header__link govuk-header__link--homepage">
          |        <span class="govuk-header__logotype">
          |
          |
          |            <svg
          |            aria-hidden="true"
          |            focusable="false"
          |            class="govuk-header__logotype-crown"
          |            xmlns="http://www.w3.org/2000/svg"
          |            viewBox="0 0 32 30"
          |            height="30"
          |            width="32"
          |            ><path
          |                fill="currentColor" fill-rule="evenodd"
          |                d="M22.6 10.4c-1 .4-2-.1-2.4-1-.4-.9.1-2 1-2.4.9-.4 2 .1 2.4 1s-.1 2-1 2.4m-5.9 6.7c-.9.4-2-.1-2.4-1-.4-.9.1-2 1-2.4.9-.4 2 .1 2.4 1s-.1 2-1 2.4m10.8-3.7c-1 .4-2-.1-2.4-1-.4-.9.1-2 1-2.4.9-.4 2 .1 2.4 1s0 2-1 2.4m3.3 4.8c-1 .4-2-.1-2.4-1-.4-.9.1-2 1-2.4.9-.4 2 .1 2.4 1s-.1 2-1 2.4M17 4.7l2.3 1.2V2.5l-2.3.7-.2-.2.9-3h-3.4l.9 3-.2.2c-.1.1-2.3-.7-2.3-.7v3.4L15 4.7c.1.1.1.2.2.2l-1.3 4c-.1.2-.1.4-.1.6 0 1.1.8 2 1.9 2.2h.7c1-.2 1.9-1.1 1.9-2.1 0-.2 0-.4-.1-.6l-1.3-4c-.1-.2 0-.2.1-.3m-7.6 5.7c.9.4 2-.1 2.4-1 .4-.9-.1-2-1-2.4-.9-.4-2 .1-2.4 1s0 2 1 2.4m-5 3c.9.4 2-.1 2.4-1 .4-.9-.1-2-1-2.4-.9-.4-2 .1-2.4 1s.1 2 1 2.4m-3.2 4.8c.9.4 2-.1 2.4-1 .4-.9-.1-2-1-2.4-.9-.4-2 .1-2.4 1s0 2 1 2.4m14.8 11c4.4 0 8.6.3 12.3.8 1.1-4.5 2.4-7 3.7-8.8l-2.5-.9c.2 1.3.3 1.9 0 2.7-.4-.4-.8-1.1-1.1-2.3l-1.2 4c.7-.5 1.3-.8 2-.9-1.1 2.5-2.6 3.1-3.5 3-1.1-.2-1.7-1.2-1.5-2.1.3-1.2 1.5-1.5 2.1-.1 1.1-2.3-.8-3-2-2.3 1.9-1.9 2.1-3.5.6-5.6-2.1 1.6-2.1 3.2-1.2 5.5-1.2-1.4-3.2-.6-2.5 1.6.9-1.4 2.1-.5 1.9.8-.2 1.1-1.7 2.1-3.5 1.9-2.7-.2-2.9-2.1-2.9-3.6.7-.1 1.9.5 2.9 1.9l.4-4.3c-1.1 1.1-2.1 1.4-3.2 1.4.4-1.2 2.1-3 2.1-3h-5.4s1.7 1.9 2.1 3c-1.1 0-2.1-.2-3.2-1.4l.4 4.3c1-1.4 2.2-2 2.9-1.9-.1 1.5-.2 3.4-2.9 3.6-1.9.2-3.4-.8-3.5-1.9-.2-1.3 1-2.2 1.9-.8.7-2.3-1.2-3-2.5-1.6.9-2.2.9-3.9-1.2-5.5-1.5 2-1.3 3.7.6 5.6-1.2-.7-3.1 0-2 2.3.6-1.4 1.8-1.1 2.1.1.2.9-.3 1.9-1.5 2.1-.9.2-2.4-.5-3.5-3 .6 0 1.2.3 2 .9l-1.2-4c-.3 1.1-.7 1.9-1.1 2.3-.3-.8-.2-1.4 0-2.7l-2.9.9C1.3 23 2.6 25.5 3.7 30c3.7-.5 7.9-.8 12.3-.8"
          |                ></path>
          |            </svg>
          |            <!--[if IE 8]>
          |              <img src="/personal-account/hmrc-frontend/assets/govuk/images/govuk-logotype-tudor-crown.png" class="govuk-header__logotype-crown-fallback-image" width="36" height="32" alt="">
          |            <![endif]-->
          |
          |        <span class="govuk-header__logotype-text">
          |            GOV.UK
          |        </span>
          |        </span>
          |
          |          </a>
          |        </div>
          |
          |
          |        <div class="govuk-header__content">
          |          <a href="/personal-account" class="hmrc-header__service-name hmrc-header__service-name--linked">
          |            Personal tax account
          |          </a>
          |        </div>
          |
          |      </div>
          |    </div>
          |</header>
          |
          |<div class="govuk-width-container ">
          |
          |
          |<!-- ACCOUNT MENU -->
          |<nav id="secondary-nav" class="hmrc-account-menu" aria-label="Account" data-module="hmrc-account-menu">
          |<!-- LEFT ALIGNED ITEMS -->
          |
          |
          |<a href="http://localhost:9232/personal-account"
          |   class="hmrc-account-menu__link hmrc-account-menu__link--home
          |   " id="menu.left.0">
          |
          | <span class="hmrc-account-icon hmrc-account-icon--home">
          | Account home
          | </span>
          |
          |</a>
          |
          |
          |<!-- LEFT ALIGNED ITEMS -->
          |    <a id="menu.name" href="#" class="hmrc-account-menu__link hmrc-account-menu__link--menu js-hidden js-visible" tabindex="-1" aria-hidden="true" aria-expanded="false">
          |        Account menu
          |    </a>
          |    <ul class="hmrc-account-menu__main">
          |        <li class="hmrc-account-menu__link--back hidden" aria-hidden="false">
          |            <a id="menu.back" href="#" tabindex="-1" class="hmrc-account-menu__link">
          |            Back
          |            </a>
          |        </li>
          |<!-- RIGHT ALIGNED ITEMS -->
          |
          |
          |<li>
          | <a href="http://localhost:9232/personal-account/messages" class="hmrc-account-menu__link " id="menu.right.0">
          |
          |  <span class="">
          |   Messages
          |
          |  </span>
          |
          | </a>
          |</li>
          |
          |<li>
          | <a href="http://localhost:9100/track" class="hmrc-account-menu__link " id="menu.right.1">
          |
          |  <span class="">
          |   Check progress
          |
          |  </span>
          |
          | </a>
          |</li>
          |
          |<li>
          | <a href="http://localhost:9232/personal-account/profile-and-settings" class="hmrc-account-menu__link " id="menu.right.2">
          |
          |  <span class="">
          |   Profile and settings
          |
          |  </span>
          |
          | </a>
          |</li>
          |
          |<li>
          | <a href="/personal-account/signout?continueUrl=http%3A%2F%2Flocalhost%3A9514%2Ffeedback%2FPERTAX" class="hmrc-account-menu__link " id="menu.right.3">
          |
          |  <span class="">
          |   Sign out
          |
          |  </span>
          |
          | </a>
          |</li>
          |
          |
          |<!-- RIGHT ALIGNED ITEMS -->
          |    </ul>
          |</nav>
          |
          |<nav class="hmrc-language-select"
          |     aria-label="Language switcher">
          |  <ul class="hmrc-language-select__list">
          |
          |          <li class="hmrc-language-select__list-item">
          |            <span aria-current="true">English</span>
          |          </li>
          |          <li class="hmrc-language-select__list-item">
          |            <a href="/personal-account/hmrc-frontend/language/cy"
          |               hreflang="cy"
          |               lang="cy"
          |               rel="alternate"
          |               class="govuk-link"
          |               data-journey-click="link - click:lang-select:Cymraeg"
          |            >
          |              <span class="govuk-visually-hidden">Newid yr iaith ir Gymraeg</span>
          |              <span aria-hidden="true">Cymraeg</span>
          |            </a>
          |          </li>
          |  </ul>
          |</nav>
          |
          |    <main class="govuk-main-wrapper govuk-main-wrapper--auto-spacing" id="main-content" role="main">
          |
          |
          |<div class="govuk-grid-row">
          |  <div class="govuk-grid-column-full">
          |    <header class="hmrc-page-heading">
          |        <h1 class="govuk-heading-xl govuk-!-margin-bottom-2">Firstname Lastname</h1>
          |        <p class="hmrc-caption govuk-caption-xl"><span class="govuk-visually-hidden">This section is </span>Account home</p>
          |    </header>
          |
          |
          |        <div class="govuk-!-padding-bottom-6 govuk-!-padding-top-2">
          |            <p class="govuk-phase-banner__content">
          |                <strong class="govuk-tag govuk-phase-banner__content__tag">
          |                BREATHING SPACE
          |                </strong>
          |                <span class="govuk-phase-banner__text">
          |                    <a class="govuk-link govuk-link--no-visited-state" href="/personal-account/breathing-space">Find out what it means to be in Breathing Space.</a>
          |                </span>
          |            </p>
          |        </div>
          |
          |
          |
          |        <div id="utrNo" class="govuk-inset-text govuk-!-margin-top-0">
          |  Your Self Assessment Unique Taxpayer Reference (UTR) is <span>0123456789</span>
          |</div>
          |
          |    <div class="pertax-home">
          |
          |        <h2 class="govuk-visually-hidden" id="yourAccountHeading">Your account</h2>
          |        <div class="flex-container grid-row">
          |
          |        </div>
          |
          |        <div class="flex-container grid-row" id="benefitsTiles">
          |
          |        </div>
          |    </div>
          |
          |<div class="govuk-!-margin-top-8">
          |    <hr aria-hidden="true" class="govuk-section-break govuk-section-break--m">
          |
          |
          |  <a lang="en"
          |    hreflang="en"
          |    class="govuk-link hmrc-report-technical-issue "
          |     rel="noreferrer noopener"
          |    target="_blank"
          |    href="http://localhost:9250/contact/report-technical-problem?newTab=true&amp;service=PTA&amp;referrerUrl=%2F"
          |  >Is this page not working properly? (opens in new tab)</a>
          |
          |    <hr aria-hidden="true" class="govuk-section-break govuk-section-break--m">
          |    <div class="govuk-phase-banner govuk-!-display-none-print">
          |  <p class="govuk-phase-banner__content">
          |
          |    <strong class="govuk-tag govuk-phase-banner__content__tag">
          |  beta
          |</strong>
          |
          |
          |    <span class="govuk-phase-banner__text">
          |      This is a new service – your <a class="govuk-link" href="http://localhost:9250/contact/beta-feedback?service=PTA&amp;backUrl=http%3A%2F%2Flocalhost%3A9033%2F">feedback</a> will help us to improve it.
          |    </span>
          |  </p>
          |</div>
          |
          |</div>
          |
          |  </div>
          |</div>
          |
          |    </main>
          |</div>
          |  <footer class="govuk-footer " role="contentinfo">
          | <div class="govuk-width-container ">
          |
          |  <div class="govuk-footer__meta">
          |   <div class="govuk-footer__meta-item govuk-footer__meta-item--grow">
          |
          |     <h2 class="govuk-visually-hidden">Support links</h2>
          |
          |
          |      <ul class="govuk-footer__inline-list">
          |
          |       <li class="govuk-footer__inline-list-item">
          |        <a class="govuk-footer__link" href="/help/cookies">
          |        Cookies
          |        </a>
          |       </li>
          |
          |       <li class="govuk-footer__inline-list-item">
          |        <a class="govuk-footer__link" href="http://localhost:12346/accessibility-statement/personal-account?referrerUrl=http%3A%2F%2Flocalhost%3A12346%2F">
          |        Accessibility statement
          |        </a>
          |       </li>
          |
          |       <li class="govuk-footer__inline-list-item">
          |        <a class="govuk-footer__link" href="/help/privacy">
          |        Privacy policy
          |        </a>
          |       </li>
          |
          |       <li class="govuk-footer__inline-list-item">
          |        <a class="govuk-footer__link" href="/help/terms-and-conditions">
          |        Terms and conditions
          |        </a>
          |       </li>
          |
          |       <li class="govuk-footer__inline-list-item">
          |        <a class="govuk-footer__link" href="https://www.gov.uk/help">
          |        Help using GOV.UK
          |        </a>
          |       </li>
          |
          |       <li class="govuk-footer__inline-list-item">
          |        <a class="govuk-footer__link" href="https://www.gov.uk/government/organisations/hm-revenue-customs/contact">
          |        Contact
          |        </a>
          |       </li>
          |
          |       <li class="govuk-footer__inline-list-item">
          |        <a class="govuk-footer__link" href="https://www.gov.uk/cymraeg" lang="cy" hreflang="cy">
          |        Rhestr o Wasanaethau Cymraeg
          |        </a>
          |       </li>
          |
          |      </ul>
          |
          |    <svg
          |    aria-hidden="true"
          |    focusable="false"
          |    class="govuk-footer__licence-logo"
          |    xmlns="http://www.w3.org/2000/svg"
          |    viewBox="0 0 483.2 195.7"
          |    height="17"
          |    width="41"
          |    >
          |     <path
          |     fill="currentColor"
          |     d="M421.5 142.8V.1l-50.7 32.3v161.1h112.4v-50.7zm-122.3-9.6A47.12 47.12 0 0 1 221 97.8c0-26 21.1-47.1 47.1-47.1 16.7 0 31.4 8.7 39.7 21.8l42.7-27.2A97.63 97.63 0 0 0 268.1 0c-36.5 0-68.3 20.1-85.1 49.7A98 98 0 0 0 97.8 0C43.9 0 0 43.9 0 97.8s43.9 97.8 97.8 97.8c36.5 0 68.3-20.1 85.1-49.7a97.76 97.76 0 0 0 149.6 25.4l19.4 22.2h3v-87.8h-80l24.3 27.5zM97.8 145c-26 0-47.1-21.1-47.1-47.1s21.1-47.1 47.1-47.1 47.2 21 47.2 47S123.8 145 97.8 145"
          |     />
          |    </svg>
          |    <span class="govuk-footer__licence-description">
          |     All content is available under the
          |     <a
          |     class="govuk-footer__link"
          |     href="https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/"
          |     rel="license"
          |     > Open Government Licence v3.0</a>, except where otherwise stated
          |    </span>
          |   </div>
          |   <div class="govuk-footer__meta-item">
          |    <a
          |    class="govuk-footer__link govuk-footer__copyright-logo"
          |    href="https://www.nationalarchives.gov.uk/information-management/re-using-public-sector-information/uk-government-licensing-framework/crown-copyright/"
          |    >© Crown copyright</a>
          |   </div>
          |  </div>
          | </div>
          |</footer>
          |
          |  <script src="/personal-account/hmrc-frontend/assets/hmrc-frontend-5.66.0.min.js" ></script>
          |    <script  src='/personal-account/assets/javascripts/webChat.js'></script>
          |    <script  src='/personal-account/assets/javascripts/card.js'></script>
          |    <script  src='/personal-account/assets/javascripts/pertaxBacklink.js'></script>
          |    <script src="/personal-account/hmrc-frontend/assets/accessible-autocomplete-5.66.0.js" ></script>
          |    <script src="/personal-account/sca-wrapper/assets/pta.js" ></script>
          |
          |  </body>
          |</html>
          |""".stripMargin.replaceAll("\\s", "")

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(currentRequest)

      status(result) mustBe OK
      contentAsString(result).replaceAll("\\s", "") mustBe expectedOutput
    }
  }
}
