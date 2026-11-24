import unittest
from build_metrics import build


class MetricsTests(unittest.TestCase):
    def setUp(self):
        self.report = dict(dataset="synthetic-eval-v1", cases=6, liveModel=False,
                           statusAccuracy=1, sourcePresenceAccuracy=1, supportedTermAccuracy=1)

    def test_preserves_observed_values_and_provenance(self):
        self.report["statusAccuracy"] = 0.5
        result = build(self.report, "a" * 40, "123")
        self.assertEqual(result["statusAccuracy"], 0.5)
        self.assertTrue(result["provenance"]["runUrl"].endswith("/123"))

    def test_rejects_unmeasured_invalid_or_live_reports(self):
        for key, value in [("liveModel", True), ("cases", 0), ("cases", True),
                           ("statusAccuracy", float("nan")), ("statusAccuracy", 1.1),
                           ("supportedTermAccuracy", None), ("sourcePresenceAccuracy", True)]:
            with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                build({**self.report, key: value}, "a" * 40, "123")

    def test_rejects_missing_origin(self):
        with self.assertRaises(ValueError):
            build(self.report, "main", "123")


if __name__ == "__main__":
    unittest.main()
