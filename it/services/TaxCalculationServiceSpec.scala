//package services
//
//import helpers.IntegrationHelpers
//import models.OverpaidStatus.Refund
//import models.UnderpaidStatus.{PartPaid, PaymentDue}
//import models._
//import org.joda.time.{DateTime, LocalDate}
//import uk.gov.hmrc.domain.Nino
//import uk.gov.hmrc.time.CurrentTaxYear
//
//
//class TaxCalculationServiceSpec extends IntegrationHelpers with CurrentTaxYear {
//
//  lazy val serviceUnderTest = app.injector.instanceOf[TaxCalculationService]
//
//  val cyMinusOne = current.currentYear-1
//  val cyMinusTwo = current.currentYear-2
//
//  "getTaxYearReconciliations" should {
//
//    "return a successful response" when {
//
//      "data is successfully retrieved for Balanced status" in {
//
//        val result = serviceUnderTest.getTaxYearReconciliations(Nino("********"))(hcWithBearer("********"))
//        await(result) shouldBe List(
//          TaxYearReconciliation(cyMinusTwo, Balanced),
//          TaxYearReconciliation(cyMinusOne, Balanced)
//        )
//      }
//
//
//      "data is successfully retrieved for Balanced SA status" in {
//
//        val result = serviceUnderTest.getTaxYearReconciliations(Nino("********"))(hcWithBearer("********"))
//
//        await(result) shouldBe List(
//          TaxYearReconciliation(cyMinusTwo, BalancedNoEmployment),
//          TaxYearReconciliation(cyMinusOne, BalancedSa)
//        )
//      }
//
//      "data is successfully retrieved for Underpaid Tolerance status" in {
//
//        val result = serviceUnderTest.getTaxYearReconciliations(Nino("********"))(hcWithBearer("********"))
//
//        await(result) shouldBe List(
//          TaxYearReconciliation(cyMinusTwo, UnderpaidTolerance),
//          TaxYearReconciliation(cyMinusOne, BalancedNoEmployment)
//        )
//      }
//
//      "data is successfully retrieved for Overpaid Tolerance status" in {
//
//        val result = serviceUnderTest.getTaxYearReconciliations(Nino("********"))(hcWithBearer("********"))
//
//        await(result) shouldBe List(
//          TaxYearReconciliation(cyMinusTwo, NotReconciled),
//          TaxYearReconciliation(cyMinusOne, OverpaidTolerance)
//        )
//      }
//
//      "data is successfully retrieved for Balanced No Employment status" in {
//
//        val result = serviceUnderTest.getTaxYearReconciliations(Nino("********"))(hcWithBearer("********"))
//
//        await(result) shouldBe List(
//          TaxYearReconciliation(cyMinusTwo, BalancedNoEmployment),
//          TaxYearReconciliation(cyMinusOne, BalancedSa)
//        )
//      }
//
//      "data is successfully retrieved for Underpaid status" in {
//
//        val result = serviceUnderTest.getTaxYearReconciliations(Nino("********"))(hcWithBearer("********"))
//
//        await(result) shouldBe List(
//          TaxYearReconciliation(cyMinusTwo, Underpaid(Some(1500.25), None, PaymentDue)),
//          TaxYearReconciliation(cyMinusOne, Underpaid(Some(1000.0), None, PaymentDue))
//          )
//        }
//
//      "data is successfully retrieved for Overpaid status" in {
//
//        val result = serviceUnderTest.getTaxYearReconciliations(Nino("********"))(hcWithBearer("********"))
//
//        await(result) shouldBe List(
//          TaxYearReconciliation(cyMinusTwo, Underpaid(Some(500.0),Some(new LocalDate("2018-02-19")),PartPaid)),
//          TaxYearReconciliation(cyMinusOne, Overpaid(Some(84.23), Refund))
//        )
//      }
//
//      "data is successfully retrieved a NotReconciled status where there is no CY-2" in {
//
//        val result = serviceUnderTest.getTaxYearReconciliations(Nino("********"))(hcWithBearer("********"))
//
//        await(result) should contain(TaxYearReconciliation(cyMinusTwo, NotReconciled))
//      }
//    }
//
//    "handle a 401 response by returning an empty list" in {
//
//      val result = serviceUnderTest.getTaxYearReconciliations(Nino("********"))
//
//      await(result) shouldBe List()
//    }
//
//    "handle a 404 response by returning an empty list" in {
//
//      val result = serviceUnderTest.getTaxYearReconciliations(Nino("********"))(hcWithBearer("********"))
//
//      await(result) shouldBe List()
//    }
//
//    "handle a 500 response by returning an empty list" in {
//
//      val result = serviceUnderTest.getTaxYearReconciliations(Nino("********"))(hcWithBearer("********"))
//
//      await(result) shouldBe List()
//    }
//  }
//
//  override def now: () => DateTime = ()=>DateTime.now
//}
