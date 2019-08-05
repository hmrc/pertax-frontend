package services

import config.ConfigDecorator
import models.OverpaidStatus.Refund
import models.UnderpaidStatus.PartPaid
import models.{Balanced, BalancedNoEmployment, BalancedSa, NotReconciled, Overpaid, TaxYearReconciliation, Underpaid}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import services.http.SimpleHttp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.Random

class TaxCalculationServiceSpec extends UnitSpec with GuiceOneAppPerSuite {

  lazy val serviceUnderTest = app.injector.instanceOf[TaxCalculationService]
  lazy val config = app.injector.instanceOf[ConfigDecorator]
  lazy val http = app.injector.instanceOf[SimpleHttp]

  implicit val hc = HeaderCarrier()

  def credId = Random.alphanumeric.take(8).mkString

  def jsonPayload(nino: String) = Json.parse(s"""{
                      |  "credId": "$credId",
                      |  "affinityGroup": "Individual",
                      |  "confidenceLevel": 200,
                      |  "credentialStrength": "strong",
                      |  "enrolments": [
                      |  {
                      |      "key": "IR-SA",
                      |      "identifiers": [
                      |        {
                      |          "key": "UTR",
                      |          "value": "123456789"
                      |        }
                      |      ],
                      |      "state": "Activated"
                      |    }
                      |  ],
                      |  "gatewayToken": "SomeToken",
                      |  "groupIdentifier": "SomeGroupIdentifier",
                      |  "nino": "$nino"
                      |}""".stripMargin)

  def getBearerToken(nino: String): String =
    await(
      http.post(s"${config.authLoginApiService}/government-gateway/session/login", jsonPayload(nino))(
        response => response.header("AUTHORIZATION").getOrElse(""),
        e => fail(e.getMessage)
      )
    )

  def hcWithBearer(nino: String): HeaderCarrier = hc.withExtraHeaders(("AUTHORIZATION", getBearerToken(nino)))

  "getTaxYearReconciliations" should {

    "return a successful response" when {

      "data is successfully retrieved for Balanced status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("JE989013A"), 2017, 2019)(hcWithBearer("JE989013A"))

        await(result) should contain(TaxYearReconciliation(2017, Balanced))
      }


      "data is successfully retrieved for Balanced SA status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("EG106313A"), 2017, 2019)(hcWithBearer("EG106313A"))

        await(result) should contain(TaxYearReconciliation(2018, BalancedSa))
      }

      "data is successfully retrieved for Balanced No Employment status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("EG106313A"), 2017, 2019)(hcWithBearer("EG106313A"))

        await(result) should contain(TaxYearReconciliation(2017, BalancedNoEmployment))
      }

      "data is successfully retrieved for Underpaid status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("LE064015A"), 2017, 2019)(hcWithBearer("LE064015A"))

        await(result) should contain(TaxYearReconciliation(2017, Underpaid(Some(1000.0), None, PartPaid)))
      }

      "data is successfully retrieved for Overpaid status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("AA000003A"), 2017, 2019)(hcWithBearer("AA000003A"))

        await(result) should contain(TaxYearReconciliation(2018, Overpaid(Some(307.5), Refund)))
      }

      "data is successfully retrieved for Not Reconciled status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("EJ174713A"), 2017, 2019)(hcWithBearer("EJ174713A"))

        await(result) should contain(TaxYearReconciliation(2017, NotReconciled))
      }
    }

    "handle a 401 response from taxcalc as a reconciliation with Not Reconciled status" in {

      val result = serviceUnderTest.getTaxYearReconciliations(Nino("AA000003A"), 2017, 2019)

      await(result) should contain(TaxYearReconciliation(2017, NotReconciled))
    }

    "handle a 404 response from taxcalc as a reconciliation with Not Reconciled status" in {

      val result = serviceUnderTest.getTaxYearReconciliations(Nino("ZP917642D"), 2017, 2019)(hcWithBearer("ZP917642D"))

      await(result) should contain(TaxYearReconciliation(2017, NotReconciled))
    }

    "handle a 500 response from taxcalc as a reconciliation with Not Reconciled status" in {

      val result = serviceUnderTest.getTaxYearReconciliations(Nino("CS700100A"), 2017, 2019)(hcWithBearer("CS700100A"))

      await(result) should contain(TaxYearReconciliation(2017, NotReconciled))
    }
  }
}
