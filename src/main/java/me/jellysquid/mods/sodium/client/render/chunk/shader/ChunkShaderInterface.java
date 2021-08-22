package me.jellysquid.mods.sodium.client.render.chunk.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformBlock;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformFloat;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformInt;
import me.jellysquid.mods.sodium.client.gl.shader.uniform.GlUniformMatrix4f;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import net.minecraft.util.math.Matrix4f;
import net.coderbot.iris.gl.program.ProgramSamplers;
import net.coderbot.iris.gl.program.ProgramUniforms;
import net.coderbot.iris.texunits.TextureUnit;
import org.jetbrains.annotations.Nullable;

/**
 * A forward-rendering shader program for chunks.
 */
public class ChunkShaderInterface {
    private final GlUniformFloat uniformModelScale;
    private final GlUniformFloat uniformModelOffset;
    private final GlUniformFloat uniformTextureScale;

    private final GlUniformInt uniformBlockTex;
    private final GlUniformInt uniformLightTex;

    private final GlUniformMatrix4f uniformModelViewMatrix;
    private final GlUniformMatrix4f uniformProjectionMatrix;
    private final GlUniformMatrix4f uniformModelViewProjectionMatrix;

    private final GlUniformBlock uniformBlockDrawParameters;


    @Nullable
    private final ProgramUniforms irisProgramUniforms;

    @Nullable
    private final ProgramSamplers irisProgramSamplers;
    public final GlUniformMatrix4f uniformNormalMatrix;

    // The fog shader component used by this program in order to setup the appropriate GL state
    private final ChunkShaderFogComponent fogShader;

    public ChunkShaderInterface(ShaderBindingContext context, ChunkShaderOptions options,
                                @Nullable ProgramUniforms irisProgramUniforms, @Nullable ProgramSamplers irisProgramSamplers) {
        this.uniformModelViewMatrix = context.bindUniform("u_ModelViewMatrix", GlUniformMatrix4f::new);
        this.uniformProjectionMatrix = context.bindUniform("u_ProjectionMatrix", GlUniformMatrix4f::new);
        this.uniformModelViewProjectionMatrix = context.bindUniform("u_ModelViewProjectionMatrix", GlUniformMatrix4f::new);
        this.uniformNormalMatrix = context.bindUniform("u_NormalMatrix", GlUniformMatrix4f::new);

        this.uniformBlockTex = context.bindUniform("u_BlockTex", GlUniformInt::new);
        this.uniformLightTex = context.bindUniform("u_LightTex", GlUniformInt::new);

        this.uniformModelScale = context.bindUniform("u_ModelScale", GlUniformFloat::new);
        this.uniformModelOffset = context.bindUniform("u_ModelOffset", GlUniformFloat::new);
        this.uniformTextureScale = context.bindUniform("u_TextureScale", GlUniformFloat::new);

        this.uniformBlockDrawParameters = context.bindUniformBlock("ubo_DrawParameters", 0);

        this.fogShader = options.fog().getFactory().apply(context);

        this.irisProgramUniforms = irisProgramUniforms;
        this.irisProgramSamplers = irisProgramSamplers;
    }

    public void setup(ChunkVertexType vertexType) {
        RenderSystem.activeTexture(TextureUnit.TERRAIN.getUnitId());
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(0));

        RenderSystem.activeTexture(TextureUnit.LIGHTMAP.getUnitId());
        RenderSystem.bindTexture(RenderSystem.getShaderTexture(2));

        this.uniformBlockTex.setInt(TextureUnit.TERRAIN.getSamplerId());
        this.uniformLightTex.setInt(TextureUnit.LIGHTMAP.getSamplerId());

        this.uniformModelScale.setFloat(vertexType.getModelScale());
        this.uniformModelOffset.setFloat(vertexType.getModelOffset());
        this.uniformTextureScale.setFloat(vertexType.getTextureScale());

        this.fogShader.setup();

        if (irisProgramUniforms != null) {
            irisProgramUniforms.update();
        }

        if (irisProgramSamplers != null) {
            irisProgramSamplers.update();
        }
    }

    public void setProjectionMatrix(Matrix4f matrix) {
        this.uniformProjectionMatrix.set(matrix);
    }

    public void setModelViewMatrix(Matrix4f matrix) {
        this.uniformModelViewMatrix.set(matrix);
    }

    public void setModelViewProjectionMatrix(Matrix4f matrix) {
        this.uniformModelViewProjectionMatrix.set(matrix);
    }

    public void setNormalMatrix(Matrix4f matrix) {
        this.uniformNormalMatrix.set(matrix);
    }

    public void setDrawUniforms(GlMutableBuffer buffer) {
        this.uniformBlockDrawParameters.bindBuffer(buffer);
    }
}