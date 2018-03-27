package com.pluscubed.graph.rendering;


import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.text.TextUtils;
import android.util.Log;

import com.pluscubed.graph.R;

import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Renders an object loaded from an OBJ file in OpenGL.
 */
public class GraphRenderer {
    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int COORDS_PER_VERTEX = 3;

    private static final String TAG = GraphRenderer.class.getSimpleName();

    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];

    float[][] colors = {
            {1f, 0f, 0f, 1f},
            {0f, 0f, 1f, 1f}
    };

    private int curveVertexCount;
    private int program;

    private int vertexBufferId;

    private int positionHandle;
    private int colorHandle;
    private int mvpMatrixHandle;

    public void createOnGlThread(Context context) {
        final int vertexShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, R.raw.graph_vertex);
        final int fragmentShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, R.raw.graph_fragment);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);

        positionHandle = GLES20.glGetAttribLocation(program, "a_Position");
        colorHandle = GLES20.glGetUniformLocation(program, "v_Color");
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");

        Matrix.setIdentityM(modelMatrix, 0);
    }

    public void updateVerticesBuffer(String x, String y, String z) {
        // AXES

        float[] axes = new float[]{
                //x-axis
                0f, 0f, 0f,
                10f, 0f, 0f,
                //y-axis
                0f, 0f, 0f,
                0f, 0f, 10f,
                //z-axis
                0f, 0f, 0f,
                0f, 10f, 0f,
        };

        // 3D CURVE

        Argument tArgument = new Argument("t");
        Expression xExpression = new Expression(x, tArgument);
        Expression yExpression = new Expression(y, tArgument);
        Expression zExpression = new Expression(z, tArgument);

        float ta = 0;
        float tb = (float) (6 * Math.PI);
        float increment = (float) (0.1 * Math.PI);

        int n = (int) ((tb - ta) / increment);

        float[] curve = new float[n * 3];

        for (int i = 0; i < n; i++) {
            float t = ta + i * increment;
            tArgument.setArgumentValue(t);

            curve[i * 3] = (float) (xExpression.calculate());
            curve[i * 3 + 1] = (float) (zExpression.calculate());
            curve[i * 3 + 2] = (float) (yExpression.calculate());
        }

        Log.d(TAG, TextUtils.join(",", Arrays.asList(curve)));

        curveVertexCount = curve.length / COORDS_PER_VERTEX;

        // -----

        float[] vertices = new float[axes.length + curve.length];
        System.arraycopy(axes, 0, vertices, 0, axes.length);
        System.arraycopy(curve, 0, vertices, axes.length, curve.length);

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

    }

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @param scaleFactor A separate scaling factor to apply before the {@code modelMatrix}.
     * @see android.opengl.Matrix
     */
    public void updateModelMatrix(float[] modelMatrix, float scaleFactor) {
        float[] scaleMatrix = new float[16];
        Matrix.setIdentityM(scaleMatrix, 0);
        scaleMatrix[0] = scaleFactor;
        scaleMatrix[5] = scaleFactor;
        scaleMatrix[10] = scaleFactor;
        Matrix.multiplyMM(this.modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0);
    }

    public void draw(float[] viewmtx, float[] projmtx, float lightIntensity) {
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
        GLES20.glUniform4fv(colorHandle, 1, colors[0], 0);
        GLES20.glLineWidth(25);

        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, 2);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 2, 2);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 4, 2);

        //CURVE
        GLES20.glUniform4fv(colorHandle, 1, colors[1], 0);
        GLES20.glLineWidth(15);
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 6, curveVertexCount);


        GLES20.glDisableVertexAttribArray(positionHandle);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "After draw");
    }

}
