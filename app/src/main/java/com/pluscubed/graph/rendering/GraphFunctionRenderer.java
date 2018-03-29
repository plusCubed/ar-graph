package com.pluscubed.graph.rendering;


import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.pluscubed.graph.R;

import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class GraphFunctionRenderer {
    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;

    private static final String TAG = GraphFunctionRenderer.class.getSimpleName();

    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];

    private final float[] min = new float[3];
    private final float[] max = new float[3];

    private int program;

    private int vertexBufferId;

    private int mvpMatrixHandle;
    private int positionHandle;
    private int minHandle;
    private int maxHandle;

    private int xSteps;
    private int ySteps;

    public void createOnGlThread(Context context) {
        final int vertexShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, R.raw.graph_function_vertex);
        final int fragmentShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, R.raw.graph_function_fragment);

        program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        GLES20.glUseProgram(program);

        ShaderUtil.checkGLError(TAG, "Program creation");

        positionHandle = GLES20.glGetAttribLocation(program, "a_Position");
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_ModelViewProjection");
        minHandle = GLES20.glGetUniformLocation(program, "u_Min");
        maxHandle = GLES20.glGetUniformLocation(program, "u_Max");

        ShaderUtil.checkGLError(TAG, "Program parameters");

        Matrix.setIdentityM(modelMatrix, 0);
    }

    public void updateSurface(String zString) {
        ShaderUtil.checkGLError(TAG, "before update");

        // 3D SURFACE

        Argument xArgument = new Argument("x");
        Argument yArgument = new Argument("y");
        Expression zExpression = new Expression(zString, xArgument, yArgument);

        float xa = (float) -5;
        float xb = (float) 5;
        float xIncrement = (float) .5;
        xSteps = (int) ((xb - xa) / xIncrement);

        float ya = (float) -5;
        float yb = (float) 5;
        float yIncrement = (float) .5;
        ySteps = (int) ((yb - ya) / yIncrement);

        float[] vertices = new float[xSteps * ySteps * 3];

        float minZ = Float.MAX_VALUE;
        float maxZ = Float.MIN_VALUE;
        for (int i = 0; i < xSteps; i++) {
            for (int j = 0; j < ySteps; j++) {
                float x = xa + i * xIncrement;
                float y = ya + j * yIncrement;

                xArgument.setArgumentValue(x);
                yArgument.setArgumentValue(y);

                float z = (float) (zExpression.calculate());

                vertices[(i * xSteps + j) * 3] = x;
                vertices[(i * xSteps + j) * 3 + 1] = z;
                vertices[(i * xSteps + j) * 3 + 2] = y;

                if (z < minZ)
                    minZ = z;
                if (z > maxZ)
                    maxZ = z;
            }
        }

        min[0] = xa;
        min[1] = minZ;
        min[2] = ya;

        max[0] = xb;
        max[1] = maxZ;
        max[2] = yb;

        // VERTICES

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

        ShaderUtil.checkGLError(TAG, "after update");
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

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, modelViewProjectionMatrix, 0);
        GLES20.glUniform3fv(minHandle, 1, min, 0);
        GLES20.glUniform3fv(maxHandle, 1, max, 0);

        //SURFACE

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId);
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(
                positionHandle,
                3,
                GLES20.GL_FLOAT,
                false,
                0,
                0
        );

        GLES20.glLineWidth(15);

        for (int i = 0; i < xSteps; i++)
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, ySteps * i, ySteps);

        for (int i = 0; i < ySteps; i++) {
            GLES20.glVertexAttribPointer(
                    positionHandle,
                    3,
                    GLES20.GL_FLOAT,
                    false,
                    xSteps * 3 * BYTES_PER_FLOAT,
                    i * 3 * BYTES_PER_FLOAT
            );
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, xSteps);
        }

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "After draw");
    }

}
