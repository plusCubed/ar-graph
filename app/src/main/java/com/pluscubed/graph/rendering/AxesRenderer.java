package com.pluscubed.graph.rendering;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.pluscubed.graph.arcore.rendering.ShaderUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class AxesRenderer {
    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int COORDS_PER_VERTEX = 3;

    private static final String TAG = AxesRenderer.class.getSimpleName();

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "shaders/axes.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/axes.frag";

    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];

    float[][] colors = {
            {1f, 0f, 0f, 1f},
            {0f, 1f, 0f, 1f},
            {0f, 0f, 1f, 1f}
    };

    private int program;

    private int vertexBufferId;

    private int positionHandle;
    private int colorHandle;
    private int mvpMatrixHandle;

    public void createOnGlThread(Context context) throws IOException {
        final int vertexShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        final int fragmentShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        positionHandle = GLES20.glGetAttribLocation(program, "a_Position");
        colorHandle = GLES20.glGetUniformLocation(program, "v_Color");
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");

        // AXES

        float[] vertices = new float[]{
                //x-axis
                0f, 0f, 0f,
                0f, 0f, 1f,
                //y-axis
                0f, 0f, 0f,
                1f, 0f, 0f,
                //z-axis
                0f, 0f, 0f,
                0f, 1f, 0f,
        };

        // -----

        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        vertexBufferId = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);

        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * BYTES_PER_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer vertexBuffer = bb.asFloatBuffer();
        vertexBuffer
                .put(vertices)
                .position(0);

        GLES20.glBufferData(
                GLES20.GL_ARRAY_BUFFER,
                vertices.length * BYTES_PER_FLOAT,
                vertexBuffer,
                GLES20.GL_STATIC_DRAW
        );

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        Matrix.setIdentityM(modelMatrix, 0);
    }

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @param scaleFactor A separate scaling factor to apply before the {@code modelMatrix}.
     * @see Matrix
     */
    public void updateModelMatrix(float[] modelMatrix, float scaleFactor) {
        float[] scaleMatrix = new float[16];
        Matrix.setIdentityM(scaleMatrix, 0);
        scaleMatrix[0] = scaleFactor;
        scaleMatrix[5] = scaleFactor;
        scaleMatrix[10] = scaleFactor;
        Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);
    }

    public void draw(float[] viewmtx, float[] projmtx, float[] colorCorrectionRgba) {
        ShaderUtil.checkGLError(TAG, "Before draw");

        // Build the ModelView and ModelViewProjection matrices
        // for calculating object position and light.
        Matrix.multiplyMM(modelViewMatrix, 0, viewmtx, 0, modelMatrix, 0);
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projmtx, 0, modelViewMatrix, 0);

        GLES20.glUseProgram(program);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(
                positionHandle,
                COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                0,
                0
        );


        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjectionMatrix, 0);

        //AXES
        GLES20.glLineWidth(15);

        GLES20.glUniform4fv(colorHandle, 1, colors[0], 0);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, 2);

        GLES20.glUniform4fv(colorHandle, 1, colors[1], 0);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 2, 2);

        GLES20.glUniform4fv(colorHandle, 1, colors[2], 0);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 4, 2);


        GLES20.glDisableVertexAttribArray(positionHandle);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "After draw");
    }

}
