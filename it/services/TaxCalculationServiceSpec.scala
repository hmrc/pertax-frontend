package services

import config.ConfigDecorator
import models.OverpaidStatus.Refund
import models.UnderpaidStatus.{PartPaid, PaymentDue}
import models._
import org.joda.time.{DateTime, LocalDate}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import services.http.SimpleHttp
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.time.CurrentTaxYear

import scala.util.Random

class TaxCalculationServiceSpec extends UnitSpec with GuiceOneAppPerSuite with CurrentTaxYear {

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

  val cyMinusOne = current.currentYear-1
  val cyMinusTwo = current.currentYear-2

  "getTaxYearReconciliations" should {

    "return a successful response" when {

      "data is successfully retrieved for Balanced status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("JE989013A"))(hcWithBearer("JE989013A"))
        await(result) shouldBe List(
          TaxYearReconciliation(cyMinusTwo, Balanced),
          TaxYearReconciliation(cyMinusOne, Balanced)
        )
      }


      "data is successfully retrieved for Balanced SA status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("EG106313A"))(hcWithBearer("EG106313A"))

        await(result) shouldBe List(
          TaxYearReconciliation(cyMinusTwo, BalancedNoEmployment),
          TaxYearReconciliation(cyMinusOne, BalancedSa)
        )
      }

      "data is successfully retrieved for Underpaid Tolerance status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("ER872414A"))(hcWithBearer("ER872414A"))

        await(result) shouldBe List(
          TaxYearReconciliation(cyMinusTwo, UnderpaidTolerance),
          TaxYearReconciliation(cyMinusOne, BalancedNoEmployment)
        )
      }

      "data is successfully retrieved for Overpaid Tolerance status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("EJ174713A"))(hcWithBearer("EJ174713A"))

        await(result) shouldBe List(
          TaxYearReconciliation(cyMinusTwo, NotReconciled),
          TaxYearReconciliation(cyMinusOne, OverpaidTolerance)
        )
      }

      "data is successfully retrieved for Balanced No Employment status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("EG106313A"))(hcWithBearer("EG106313A"))

        await(result) shouldBe List(
          TaxYearReconciliation(cyMinusTwo, BalancedNoEmployment),
          TaxYearReconciliation(cyMinusOne, BalancedSa)
        )
      }

      "data is successfully retrieved for Underpaid status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("LE064015A"))(hcWithBearer("LE064015A"))

        await(result) shouldBe List(
          TaxYearReconciliation(cyMinusTwo, Underpaid(Some(1500.25), None, PaymentDue)),
          TaxYearReconciliation(cyMinusOne, Underpaid(Some(1000.0), None, PaymentDue))
          )
        }

      "data is successfully retrieved for Overpaid status" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("AA000003A"))(hcWithBearer("AA000003A"))

        await(result) shouldBe List(
          TaxYearReconciliation(cyMinusTwo, Underpaid(Some(500.0),Some(new LocalDate("2018-02-19")),PartPaid)),
          TaxYearReconciliation(cyMinusOne, Overpaid(Some(84.23), Refund))
        )
      }

      "data is successfully retrieved a NotReconciled status where there is no CY-2" in {

        val result = serviceUnderTest.getTaxYearReconciliations(Nino("EJ174713A"))(hcWithBearer("EJ174713A"))

        await(result) should contain(TaxYearReconciliation(cyMinusTwo, NotReconciled))
      }
    }

    "handle a 401 response by returning an empty list" in {

      val result = serviceUnderTest.getTaxYearReconciliations(Nino("AA000003A"))

      await(result) shouldBe List()
    }

    "handle a 404 response by returning an empty list" in {

      val result = serviceUnderTest.getTaxYearReconciliations(Nino("ZP917642D"))(hcWithBearer("ZP917642D"))

      await(result) shouldBe List()
    }

    "handle a 500 response by returning an empty list" in {

      val result = serviceUnderTest.getTaxYearReconciliations(Nino("CS700100A"))(hcWithBearer("CS700100A"))

      await(result) shouldBe List()
    }
  }

  override def now: () => DateTime = ()=>DateTime.now
}
