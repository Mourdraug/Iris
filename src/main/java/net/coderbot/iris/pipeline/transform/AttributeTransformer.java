package net.coderbot.iris.pipeline.transform;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import io.github.douira.glsl_transformer.GLSLParser;
import io.github.douira.glsl_transformer.ast.node.Identifier;
import io.github.douira.glsl_transformer.ast.node.TranslationUnit;
import io.github.douira.glsl_transformer.ast.node.basic.ASTNode;
import io.github.douira.glsl_transformer.ast.node.external_declaration.DeclarationExternalDeclaration;
import io.github.douira.glsl_transformer.ast.query.Root;
import io.github.douira.glsl_transformer.ast.transform.ASTInjectionPoint;
import io.github.douira.glsl_transformer.ast.transform.ASTTransformer;
import io.github.douira.glsl_transformer.ast.transform.Matcher;
import net.coderbot.iris.gl.shader.ShaderType;

/**
 * Implements AttributeShaderTransformer using glsl-transformer AST
 * transformation methods.
 */
public class AttributeTransformer {
	private static List<ASTNode> nodeList = new ArrayList<>();

	public static void accept(
			ASTTransformer<?> transformer,
			TranslationUnit tree,
			Root root,
			AttributeParameters parameters) {

		if (parameters.inputs.lightmap) {
			// original: transformations.replaceExact("gl_MultiTexCoord1",
			// "gl_MultiTexCoord2");
			root.identifierIndex.renameAll("gl_MultiTexCoord1", "gl_MultiTexCoord2");
		}

		Stream<Identifier> stream = Stream.empty();

		// original: transformations.replaceExact("gl_MultiTexCoord1", "vec4(240.0,
		// 240.0, 0.0, 1.0)");
		// original: transformations.replaceExact("gl_MultiTexCoord2", "vec4(240.0,
		// 240.0, 0.0, 1.0)");
		if (!parameters.inputs.lightmap) {
			stream = Stream.concat(stream,
					root.identifierIndex.get("gl_MultiTexCoord1").stream());
			stream = Stream.concat(stream,
					root.identifierIndex.get("gl_MultiTexCoord2").stream());
		}

		// original: transformations.define("gl_MultiTexCoord0", "vec4(240.0, 240.0,
		// 0.0, 1.0)");
		if (parameters.inputs.overlay) {
			stream = Stream.concat(stream,
					root.identifierIndex.get("gl_MultiTexCoord0").stream());
		}

		nodeList.clear();
		stream.forEach(nodeList::add);
		for (ASTNode identifier : nodeList) {
			identifier.getParent().replaceByAndDelete(
					transformer.parseExpression(identifier, "vec4(240.0, 240.0, 0.0, 1.0)"));
		}

		// original: patchTextureMatrices(transformations, inputs.lightmap);
		patchTextureMatrices(transformer, tree, root, parameters.inputs.lightmap);

		if (parameters.inputs.overlay) {
			patchOverlayColor(transformer, tree, root, parameters);
		}

		if (parameters.type == ShaderType.VERTEX
				&& root.identifierIndex.has("gl_MultiTexCoord3")
				&& !root.identifierIndex.has("mc_midTexCoord")) {
			// TODO: proper type conversion, see original code
			// original: transformations.replaceExact("gl_MultiTexCoord3",
			// "mc_midTexCoord");
			root.identifierIndex.renameAll("gl_MultiTexCoord3", "mc_midTexCoord");

			// original:
			// transformations.injectLine(Transformations.InjectionPoint.BEFORE_CODE,
			// "attribute vec4 mc_midTexCoord;");
			tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_FUNCTIONS,
					"attribute vec4 mc_midTexCoord;");
		}
	}

	private static void patchTextureMatrices(
			ASTTransformer<?> transformer,
			TranslationUnit tree,
			Root root,
			boolean hasLightmap) {
		// original: transformations.replaceExact("gl_TextureMatrix",
		// "iris_TextureMatrix");
		root.identifierIndex.renameAll("gl_TextureMatrix", "iris_TextureMatrix");

		// original:
		// transformations.injectLine(Transformations.InjectionPoint.BEFORE_CODE, "const
		// float iris_ONE_OVER_256 = 0.00390625;\n");
		// original:
		// transformations.injectLine(Transformations.InjectionPoint.BEFORE_CODE, "const
		// float iris_ONE_OVER_32 = iris_ONE_OVER_256 * 8;\n");
		tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_FUNCTIONS,
				"const float iris_ONE_OVER_256 = 0.00390625;");
		tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_FUNCTIONS,
				"const float iris_ONE_OVER_32 = iris_ONE_OVER_256 * 8;");
		if (hasLightmap) {
			// original:
			// transformations.injectLine(Transformations.InjectionPoint.BEFORE_CODE, "mat4
			// iris_LightmapTextureMatrix = gl_TextureMatrix[2];\n");
			tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_FUNCTIONS,
					"mat4 iris_LightmapTextureMatrix = gl_TextureMatrix[2];");
		} else {
			// original:
			// transformations.injectLine(Transformations.InjectionPoint.BEFORE_CODE, "mat4
			// iris_LightmapTextureMatrix =" +
			// "mat4(iris_ONE_OVER_256, 0.0, 0.0, 0.0," +
			// " 0.0, iris_ONE_OVER_256, 0.0, 0.0," +
			// " 0.0, 0.0, iris_ONE_OVER_256, 0.0," +
			// " iris_ONE_OVER_32, iris_ONE_OVER_32, iris_ONE_OVER_32,
			// iris_ONE_OVER_256);");
			tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_FUNCTIONS,
					"mat4 iris_LightmapTextureMatrix =" +
							"mat4(iris_ONE_OVER_256, 0.0, 0.0, 0.0," +
							"     0.0, iris_ONE_OVER_256, 0.0, 0.0," +
							"     0.0, 0.0, iris_ONE_OVER_256, 0.0," +
							"     iris_ONE_OVER_32, iris_ONE_OVER_32, iris_ONE_OVER_32, iris_ONE_OVER_256);");
		}

		// original:
		// transformations.injectLine(Transformations.InjectionPoint.BEFORE_CODE, "mat4
		// iris_TextureMatrix[8] = mat4[8](" +
		// "gl_TextureMatrix[0]," +
		// "iris_LightmapTextureMatrix," +
		// "mat4(1.0)," +
		// "mat4(1.0)," +
		// "mat4(1.0)," +
		// "mat4(1.0)," +
		// "mat4(1.0)," +
		// "mat4(1.0)" +
		// ");");
		tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_FUNCTIONS,
				"mat4 iris_TextureMatrix[8] = mat4[8](" +
						"gl_TextureMatrix[0]," +
						"iris_LightmapTextureMatrix," +
						"mat4(1.0)," +
						"mat4(1.0)," +
						"mat4(1.0)," +
						"mat4(1.0)," +
						"mat4(1.0)," +
						"mat4(1.0)" +
						");");
	}

	private static final Matcher uniformVec4EntityColor = new Matcher(
			"uniform vec4 entityColor;", GLSLParser::externalDeclaration);

	private static void patchOverlayColor(
			ASTTransformer<?> transformer,
			TranslationUnit tree,
			Root root,
			AttributeParameters parameters) {
		// original: transformations.replaceRegex("uniform\\s+vec4\\s+entityColor;",
		// "");
		nodeList.clear();
		root.identifierIndex.getStream("entityColor")
				.map(identifier -> identifier.getAncestor(DeclarationExternalDeclaration.class))
				.distinct()
				.forEach(externalDeclaration -> {
					if (uniformVec4EntityColor.matches(externalDeclaration)) {
						nodeList.add(externalDeclaration);
					}
				});
		for (ASTNode node : nodeList) {
			node.detachAndDelete();
		}

		if (parameters.type == ShaderType.VERTEX) {
			// transformations.injectLine(Transformations.InjectionPoint.BEFORE_CODE,
			// "uniform sampler2D iris_overlay;");
			tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"uniform sampler2D iris_overlay;");
			// transformations.injectLine(Transformations.InjectionPoint.BEFORE_CODE,
			// "varying vec4 entityColor;");
			tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"varying vec4 entityColor;");

			// transformations.replaceExact("main", "irisMain_overlayColor");
			root.identifierIndex.renameAll("main", "irisMain_overlayColor");

			// transformations.injectLine(Transformations.InjectionPoint.END, "void main()
			// {\n" +
			// " vec4 overlayColor = texture2D(iris_overlay, (gl_TextureMatrix[1] *
			// gl_MultiTexCoord1).xy);\n" +
			// " entityColor = vec4(overlayColor.rgb, 1.0 - overlayColor.a);\n" +
			// "\n" +
			// " irisMain_overlayColor();\n" +
			// "}");
			tree.parseAndInjectNode(transformer, ASTInjectionPoint.END,
					"void main() {" +
							"vec4 overlayColor = texture2D(iris_overlay, (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);" +
							"entityColor = vec4(overlayColor.rgb, 1.0 - overlayColor.a);" +
							"irisMain_overlayColor(); }");
		} else if (parameters.type == ShaderType.GEOMETRY) {
			// transformations.replaceExact("entityColor", "entityColor[0]");
			nodeList.clear();
			nodeList.addAll(root.identifierIndex.get("entityColor"));
			for (ASTNode identifier : nodeList) {
				identifier.getParent().replaceByAndDelete(
						transformer.parseExpression(identifier,
								"entityColor[0]"));
			}

			// transformations.injectLine(Transformations.InjectionPoint.BEFORE_CODE, "out
			// vec4 entityColorGS;");
			tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"out vec4 entityColorGS;");

			// transformations.injectLine(Transformations.InjectionPoint.BEFORE_CODE, "in
			// vec4 entityColor[];");
			tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"in vec4 entityColor[];");

			// transformations.replaceExact("main", "irisMain");
			root.identifierIndex.renameAll("main", "irisMain");

			// transformations.injectLine(Transformations.InjectionPoint.END, "void main()
			// {\n" +
			// " entityColorGS = entityColor[0];\n" +
			// " irisMain();\n" +
			// "}");
			tree.parseAndInjectNode(transformer, ASTInjectionPoint.END,
					"void main() { entityColorGS = entityColor[0]; irisMain(); }");
		} else if (parameters.type == ShaderType.FRAGMENT) {
			// transformations.replaceRegex("uniform\\s+vec4\\s+entityColor;", "varying vec4
			// entityColor;");
			tree.parseAndInjectNode(transformer, ASTInjectionPoint.BEFORE_DECLARATIONS,
					"varying vec4 entityColor;");

			if (parameters.hasGeometry) {
				// transformations.replaceExact("entityColor", "entityColorGS");
				root.identifierIndex.renameAll("entityColor", "entityColorGS");
			}
		}
	}
}
