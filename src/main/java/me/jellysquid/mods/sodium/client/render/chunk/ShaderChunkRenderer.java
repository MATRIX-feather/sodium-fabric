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
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram;
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
    private final Map<BlockRenderPass, Map<ChunkShaderOptions, ChunkProgram>> gbufferPrograms = new Object2ObjectOpenHashMap<>();
    private final Map<BlockRenderPass, Map<ChunkShaderOptions, ChunkProgram>> shadowPrograms = new Object2ObjectOpenHashMap<>();

    protected final ChunkVertexType vertexType;
    protected final GlVertexFormat<ChunkMeshAttribute> vertexFormat;

    protected final RenderDevice device;

    protected ChunkProgram activeProgram;

    public ShaderChunkRenderer(RenderDevice device, ChunkVertexType vertexType) {
        this.device = device;
        this.vertexType = vertexType;
        this.vertexFormat = vertexType.getCustomVertexFormat();
    }

    // TODO: Generalize shader options
    protected ChunkProgram compileProgram(boolean isShadowPass, BlockRenderPass pass, ChunkShaderOptions options,
                                          SodiumTerrainPipeline pipeline) {
        Map<BlockRenderPass, Map<ChunkShaderOptions, ChunkProgram>> programMap =
                isShadowPass ? shadowPrograms : gbufferPrograms;

        Map<ChunkShaderOptions, ChunkProgram> programs = programMap.get(pass);

        if (programs == null) {
            programMap.put(pass, programs = new Object2ObjectOpenHashMap<>());
        }

        ChunkProgram program = programs.get(options);

        if (program == null) {
            programs.put(options, program = this.createShader(this.device, isShadowPass, pass, options, pipeline));
        }

        return program;
    }

    // TODO: Define these in the render pass itself
    protected String getShaderName(BlockRenderPass pass) {
        return switch (pass) {
            case CUTOUT -> "blocks/block_layer_cutout";
            case CUTOUT_MIPPED -> "blocks/block_layer_cutout_mipped";
            case TRANSLUCENT, TRIPWIRE -> "blocks/block_layer_translucent";
            default -> "blocks/block_layer_solid";
        };
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
                new Identifier("sodium", getShaderName(pass) + ".vsh"), constants);
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
                new Identifier("sodium", getShaderName(pass) + ".fsh"), constants);
    }

    private ChunkProgram createShader(RenderDevice device, boolean isShadowPass, BlockRenderPass pass,
                                      ChunkShaderOptions options, SodiumTerrainPipeline pipeline) {
        ShaderConstants constants = options.constants();

        GlShader vertShader = createVertexShader(device, isShadowPass, pass, pipeline, constants);
        GlShader fragShader = createFragmentShader(device, isShadowPass, pass, pipeline, constants);

        try {
            return GlProgram.builder(new Identifier("sodium", "chunk_shader"))
                    .attachShader(vertShader)
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
                    .build((handle) -> {
                        ProgramUniforms uniforms = null;
                        ProgramSamplers samplers = null;

                        if (pipeline != null) {
                            uniforms = pipeline.initUniforms(handle);

                            if (isShadowPass) {
                                samplers = pipeline.initShadowSamplers(handle);
                            } else {
                                samplers = pipeline.initTerrainSamplers(handle);
                            }
                        }

                        return new ChunkProgram(device, handle, options, uniforms, samplers);
                    });
        } finally {
            vertShader.delete();
            fragShader.delete();
        }
    }

    protected void begin(BlockRenderPass pass, MatrixStack matrixStack) {
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

        ChunkShaderOptions options = new ChunkShaderOptions(ChunkFogMode.SMOOTH);

        this.activeProgram = this.compileProgram(isShadowPass, pass, options, sodiumTerrainPipeline);
        this.activeProgram.bind();
        this.activeProgram.setup(matrixStack, this.vertexType);

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

        // TODO: Bind the framebuffer to whatever fallback is specified by SodiumTerrainPipeline.
        MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
    }

    private void deletePrograms(Map<BlockRenderPass, Map<ChunkShaderOptions, ChunkProgram>> programs) {
        programs.values()
                .stream()
                .flatMap(i -> i.values().stream())
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
