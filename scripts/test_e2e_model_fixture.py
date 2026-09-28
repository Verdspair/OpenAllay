import importlib.util
import json
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("e2e-model-fixture.py")
spec = importlib.util.spec_from_file_location("e2e_model_fixture", MODULE_PATH)
fixture = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fixture)


class JavascriptFixtureTests(unittest.TestCase):
    def test_default_request_uses_current_javascript_tool_only(self):
        arguments = fixture.javascript_arguments()
        self.assertEqual(["recipes", "player", "knowledge"], arguments["roots"])
        self.assertIn("mc.recipes", arguments["source"])
        self.assertIn('require("openallay:crafting").allocate', arguments["source"])
        self.assertNotIn("search_recipes", arguments["source"])
        self.assertEqual("openallay__run_javascript", fixture.JAVASCRIPT_TOOL)

    def test_result_requires_current_tool_success_and_recipe_reference(self):
        recipe = {"id": fixture.RECIPE_ID,
                  "reference": {"sourceId": "minecraft:recipe_manager",
                                "generation": "a" * 64,
                                "recipeId": fixture.RECIPE_ID}}
        request = {"messages": [
            {"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
             "content": json.dumps({"status": "success", "value": {
                 "preview": {"recipe": recipe, "craftability": None,
                             "ingredients": [], "sources": []}}})}
        ]}
        self.assertEqual(recipe["reference"], fixture.recipe_reference(request))
        self.assertEqual(recipe, fixture.javascript_result(request)["recipe"])

    def test_fails_closed_when_tool_result_has_no_captured_recipe(self):
        request = {"messages": [{"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
                                  "content": json.dumps({"status": "failure",
                                                         "code": "missing_recipe"})}]}
        with self.assertRaises(ValueError):
            fixture.assistant_content(request, 1)

    def test_component_output_uses_the_exact_captured_reference_and_values(self):
        reference = {"sourceId": "minecraft:recipe_manager",
                     "generation": "b" * 64, "recipeId": fixture.RECIPE_ID}
        preview = {"recipe": {"reference": reference},
                   "craftability": {"craftable": True, "conclusive": True,
                                    "requestedCrafts": 1, "maximumCrafts": 2},
                   "ingredients": [{"itemId": "minecraft:iron_ingot", "required": 1,
                                    "available": 3}],
                   "sources": [{"sourceId": "patchouli:resources"}]}
        request = {"messages": [{"role": "tool", "name": fixture.JAVASCRIPT_TOOL,
                                  "content": json.dumps({"status": "success", "value": {
                                      "preview": preview}})}]}
        content = fixture.assistant_content(request, 1)
        self.assertIn(reference["generation"], content)
        self.assertIn('"craftable":true', content)
        self.assertIn('"available":3', content)
        self.assertIn("固定验收文本，不代表真实模型生成", content)


if __name__ == "__main__":
    unittest.main()
