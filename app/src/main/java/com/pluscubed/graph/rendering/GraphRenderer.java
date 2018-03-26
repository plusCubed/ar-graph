package com.pluscubed.graph.rendering;


import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.text.TextUtils;
import android.util.Log;
import com.pluscubed.graph.R;
import org.mariuszgromada.math.mxparser.Expression;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

/**
 * Renders an object loaded from an OBJ file in OpenGL.
 */
public class GraphRenderer {
    static final int COORDS_PER_VERTEX = 3;
    private static final String TAG = GraphRenderer.class.getSimpleName();
    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];
    float[][] colors = {
            {1f, 0f, 0f, 1f},
            {0f, 0f, 1f, 1f}
    };
    private float[] vertices;
    private int axesVertexCount = 6;
    private int curveVertexCount;
    private int vertexCount;
    private int vertexStride = COORDS_PER_VERTEX * 4;
    private int program;
    private FloatBuffer vertexBuffer;

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

        Expression xExpression = new Expression(x);
        Expression yExpression = new Expression(y);
        Expression zExpression = new Expression(z);

        float ta = 0;
        float tb = (float) (6 * Math.PI);
        float increment = (float) (0.1 * Math.PI);

        int n = (int) ((tb - ta) / increment);

        float[] curve = new float[n * 3];

        for (int i = 0; i < n; i++) {
            float t = ta + i * increment;
            curve[i * 3] = (float) (5 * Math.cos(t));
            curve[i * 3 + 2] = (float) (5 * Math.sin(t));
            curve[i * 3 + 1] = t;
        }

        Log.d(TAG, TextUtils.join(",", Arrays.asList(curve)));

        curveVertexCount = curve.length / COORDS_PER_VERTEX;

        // -----

        float[] all = new float[axes.length + curve.length];
        System.arraycopy(axes, 0, all, 0, axes.length);
        System.arraycopy(curve, 0, all, axes.length, curve.length);

        vertices = all;
        vertexCount = vertices.length / COORDS_PER_VERTEX;

        // -----

        ByteBuffer bb = ByteBuffer.allocateDirect(vertices.length * 4);
        bb.order(ByteOrder.nativeOrder());

        vertexBuffer = bb.asFloatBuffer();

        vertexBuffer
                .put(vertices)
                .position(0);

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

        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(
                positionHandle,
                COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                vertexStride,
                vertexBuffer
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

        ShaderUtil.checkGLError(TAG, "After draw");
    }

}
