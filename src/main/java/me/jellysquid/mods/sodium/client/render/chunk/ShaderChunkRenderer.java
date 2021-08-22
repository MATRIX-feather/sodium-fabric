package me.jellysquid.mods.sodium.client.render.chunk;

import com.mojang.blaze3d.systems.RenderSystem;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.shader.*;
import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkFogMode;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderBindingPoints;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderOptions;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.pipeline.SodiumTerrainPipeline;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.coderbot.iris.shadows.ShadowRenderingState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;

public abstract class ShaderChunkRenderer implements ChunkRenderer {
    private final Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> gbufferPrograms = new Object2ObjectOpenHashMap<>();
    private final Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> shadowPrograms = new Object2ObjectOpenHashMap<>();

    protected final ChunkVertexType vertexType;
    protected final GlVertexFormat<ChunkMeshAttribute> vertexFormat;

    protected final RenderDevice device;

    protected GlProgram<ChunkShaderInterface> activeProgram;

    public ShaderChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        this.device = device;
        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getCustomVertexFormat();
    }

    protected GlProgram<ChunkShaderInterface> compileProgram(boolean isShadowPass, ChunkShaderOptions options,  SodiumTerrainPipeline pipeline) {
        Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> programMap =
                isShadowPass ? shadowPrograms : gbufferPrograms;

        GlProgram<ChunkShaderInterface> program = programMap.get(options);

        if (program == null) {
            programMap.put(options, program = this.createShader("blocks/block_layer_opaque", isShadowPass, pipeline, options));
        }

        return program;
    }

    private GlProgram<ChunkShaderInterface> createShader(String path, boolean isShadowPass, SodiumTerrainPipeline pipeline, ChunkShaderOptions options) {
        ShaderConstants constants = options.constants();

        GlShader vertShader = createVertexShader(device, isShadowPass, options.pass(), pipeline, constants);
        GlShader geomShader = createGeometryShader(device, isShadowPass, options.pass(), pipeline, constants);
        GlShader fragShader = createFragmentShader(device, isShadowPass, options.pass(), pipeline, constants);

        try {
            return GlProgram.builder(new Identifier("sodium", "chunk_shader"))
                    .attachShader(vertShader)
                    .attachShader(geomShader)
                    .attachShader(fragShader)
                    .bindAttribute("a_Pos", ChunkShaderBindingPoints.ATTRIBUTE_POSITION_ID)
                    .bindAttribute("a_Color", ChunkShaderBindingPoints.ATTRIBUTE_COLOR)
                    .bindAttribute("a_TexCoord", ChunkShaderBindingPoints.ATTRIBUTE_BLOCK_TEXTURE)
                    .bindAttribute("a_LightCoord", ChunkShaderBindingPoints.ATTRIBUTE_LIGHT_TEXTURE)
                    .bindAttribute("mc_Entity", ChunkShaderBindingPoints.BLOCK_ID)
                    .bindAttribute("mc_midTexCoord", ChunkShaderBindingPoints.MID_TEX_COORD)
                    .bindAttribute("at_tangent", ChunkShaderBindingPoints.TANGENT)
                    .bindAttribute("a_Normal", ChunkShaderBindingPoints.NORMAL)
                    .bindFragmentData("fragColor", ChunkShaderBindingPoints.FRAG_COLOR)
                    .bindFragmentData("iris_FragData", ChunkShaderBindingPoints.FRAG_COLOR)
                    .link((shader) -> {
                        ProgramUniforms uniforms = null;
                        ProgramSamplers samplers = null;

                        if (pipeline != null) {
                            uniforms = pipeline.initUniforms(shader.handle());

                            if (isShadowPass) {
                                samplers = pipeline.initShadowSamplers(shader.handle());
                            } else {
                                samplers = pipeline.initTerrainSamplers(shader.handle());
                            }
                        }

                        return new ChunkShaderInterface(shader, options, uniforms, samplers);
                    });
        } finally {
            vertShader.delete();
            if (geomShader != null) {
                geomShader.delete();
            }
            fragShader.delete();
        }
    }


    private GlShader createVertexShader(RenderDevice device, boolean isShadowPass, BlockRenderPass pass,
                                        SodiumTerrainPipeline pipeline, ShaderConstants constants) {
        if (pipeline != null) {
            Optional<String> irisVertexShader;
            String name;

            if (isShadowPass) {
                irisVertexShader = pipeline.getShadowVertexShaderSource();
                name = "shadow_terrain";
            } else {
                irisVertexShader = pass.isTranslucent() ? pipeline.getTranslucentVertexShaderSource() : pipeline.getTerrainVertexShaderSource();
                name = pass.isTranslucent() ? "terrain_translucent" : "terrain";
            }

            String fullName = "patched_" + name + "_for_sodium.vsh";

            if (irisVertexShader.isPresent()) {
                return new GlShader(ShaderType.VERTEX, new Identifier("iris", fullName),
                        irisVertexShader.get());
            }
        }

        return ShaderLoader.loadShader(ShaderType.VERTEX,
                new Identifier("sodium", "blocks/block_layer_opaque" + ".vsh"), constants);
    }

    private GlShader createGeometryShader(RenderDevice device, boolean isShadowPass, BlockRenderPass pass,
                                          SodiumTerrainPipeline pipeline, ShaderConstants constants) {
        if (pipeline != null) {
            Optional<String> irisGeometryShader;
            String name;

            if (isShadowPass) {
                irisGeometryShader = pipeline.getShadowGeometryShaderSource();
                name = "shadow_terrain";
            } else {
                irisGeometryShader = pass.isTranslucent() ? pipeline.getTranslucentGeometryShaderSource() : pipeline.getTerrainGeometryShaderSource();
                name = pass.isTranslucent() ? "terrain_translucent" : "terrain";
            }

            String fullName = "patched_" + name + "_for_sodium.gsh";

            if (irisGeometryShader.isPresent()) {
                return new GlShader(ShaderType.GEOMETRY, new Identifier("iris", fullName),
                        irisGeometryShader.get());
            }
        }

        return null;
    }

    private GlShader createFragmentShader(RenderDevice device, boolean isShadowPass, BlockRenderPass pass,
                                          SodiumTerrainPipeline pipeline, ShaderConstants constants) {
        if (pipeline != null) {
            Optional<String> irisFragmentShader;
            String name;

            if (isShadowPass) {
                if (pass == BlockRenderPass.CUTOUT || pass == BlockRenderPass.CUTOUT_MIPPED) {
                    irisFragmentShader = pipeline.getShadowCutoutFragmentShaderSource();
                    name = "shadow_terrain_cutout";
                } else {
                    irisFragmentShader = pipeline.getShadowFragmentShaderSource();
                    name = "shadow_terrain";
                }
            } else if (pass == BlockRenderPass.CUTOUT || pass == BlockRenderPass.CUTOUT_MIPPED) {
                irisFragmentShader = pipeline.getTerrainCutoutFragmentShaderSource();
                name = "terrain_cutout";
            } else if (pass.isTranslucent()) {
                irisFragmentShader = pipeline.getTranslucentFragmentShaderSource();
                name = "terrain_translucent";
            } else {
                irisFragmentShader = pipeline.getTerrainFragmentShaderSource();
                name = "terrain";
            }

            String fullName = "patched_" + name + "_for_sodium.fsh";

            if (irisFragmentShader.isPresent()) {
                return new GlShader(ShaderType.FRAGMENT, new Identifier("iris", fullName),
                        irisFragmentShader.get());
            }
        }

        return ShaderLoader.loadShader(ShaderType.FRAGMENT,
                new Identifier("sodium", "blocks/block_layer_opaque" + ".fsh"), constants);
    }

    protected void begin(BlockRenderPass pass) {
        if (Iris.getPipelineManager().isSodiumShaderReloadNeeded()) {
            deleteAllPrograms();
            Iris.getPipelineManager().clearSodiumShaderReloadNeeded();
        }

        WorldRenderingPipeline worldRenderingPipeline = Iris.getPipelineManager().getPipeline();
        SodiumTerrainPipeline sodiumTerrainPipeline = null;

        if (worldRenderingPipeline != null) {
            sodiumTerrainPipeline = worldRenderingPipeline.getSodiumTerrainPipeline();
        }

        boolean isShadowPass = ShadowRenderingState.areShadowsCurrentlyBeingRendered();

        ChunkShaderOptions options = new ChunkShaderOptions(ChunkFogMode.SMOOTH, pass);

        this.activeProgram = this.compileProgram(isShadowPass, options, sodiumTerrainPipeline);
        this.activeProgram.bind();
        this.activeProgram.getInterface().setup(this.vertexType);

        if (isShadowPass) {
            // No back face culling during the shadow pass
            // TODO: Hopefully this won't be necessary in the future...
            RenderSystem.disableCull();
        }

        if (sodiumTerrainPipeline != null) {
            GlFramebuffer framebuffer = null;

            if (isShadowPass) {
                framebuffer = sodiumTerrainPipeline.getShadowFramebuffer();
            } else if (pass.isTranslucent()) {
                framebuffer = sodiumTerrainPipeline.getTranslucentFramebuffer();
            } else {
                framebuffer = sodiumTerrainPipeline.getTerrainFramebuffer();
            }

            if (framebuffer != null) {
                framebuffer.bind();
            }
        }
    }

    protected void end() {
        this.activeProgram.unbind();
        this.activeProgram = null;
        ProgramUniforms.clearActiveUniforms();

        // TODO: Bind the framebuffer to whatever fallback is specified by SodiumTerrainPipeline.
        MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
    }

    private void deletePrograms(Map<ChunkShaderOptions, GlProgram<ChunkShaderInterface>> programs) {
        programs.values()
                .stream()
                .forEach(GlProgram::delete);
        programs.clear();
    }

    private void deleteAllPrograms() {
        deletePrograms(shadowPrograms);
        deletePrograms(gbufferPrograms);
    }

    @Override
    public void delete() {
        deleteAllPrograms();
    }

    @Override
    public ChunkVertexType getVertexType() {
        return this.vertexType;
    }
}