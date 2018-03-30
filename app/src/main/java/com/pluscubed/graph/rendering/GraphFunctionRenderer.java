package com.pluscubed.graph.rendering;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;

import com.pluscubed.graph.arcore.rendering.ShaderUtil;

import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class GraphFunctionRenderer {
    private static final int BYTES_PER_FLOAT = Float.SIZE / 8;
    private static final int BYTES_PER_SHORT = Short.SIZE / 8;

    private static final String TAG = GraphFunctionRenderer.class.getSimpleName();

    // Shader names.
    private static final String VERTEX_SHADER_NAME = "shaders/function.vert";
    private static final String FRAGMENT_SHADER_NAME = "shaders/function.frag";

    private final float[] modelMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16];
    private final float[] modelViewProjectionMatrix = new float[16];

    private final float[] min = new float[3];
    private final float[] max = new float[3];

    private int program;

    private int vertexBufferId;
    private int indexBufferId;

    private int indicesCount;

    private int mvpMatrixHandle;
    private int positionHandle;
    private int minHandle;
    private int maxHandle;

    private int xSteps;
    private int ySteps;

    public void createOnGlThread(Context context) throws IOException {
        final int vertexShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME);
        final int fragmentShader =
                ShaderUtil.loadGLShader(TAG, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME);

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

        float minX = (float) -3;
        float maxX = (float) 5;
        float xIncrement = (float) .2;
        xSteps = (int) ((maxX - minX) / xIncrement);

        float minY = (float) -5;
        float maxY = (float) 5;
        float yIncrement = (float) .5;
        ySteps = (int) ((maxY - minY) / yIncrement);

        // Ordered x, then y
        float[] vertices = new float[xSteps * ySteps * 3];

        float minZ = Float.MAX_VALUE;
        float maxZ = Float.MIN_VALUE;
        for (int yi = 0; yi < ySteps; yi++) {
            for (int xi = 0; xi < xSteps; xi++) {
                float x = minX + xi * xIncrement;
                float y = minY + yi * yIncrement;

                xArgument.setArgumentValue(x);
                yArgument.setArgumentValue(y);

                float z = (float) (zExpression.calculate());

                vertices[(yi * xSteps + xi) * 3] = y;
                vertices[(yi * xSteps + xi) * 3 + 1] = z;
                vertices[(yi * xSteps + xi) * 3 + 2] = x;

                if (z < minZ)
                    minZ = z;
                if (z > maxZ)
                    maxZ = z;
            }
        }

        min[0] = minX;
        min[1] = minZ;
        min[2] = minY;

        max[0] = maxX;
        max[1] = maxZ;
        max[2] = maxY;

        int[] buffers = new int[2];
        GLES20.glGenBuffers(2, buffers, 0);

        // VERTICES

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

        //Index

        indexBufferId = buffers[1];
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);

        //2 indices per segment * number of segments * lines
        short[] indices = new short[2 * (xSteps - 1) * ySteps + 2 * (ySteps - 1) * xSteps];

        int i = 0;

        // Horizontal grid lines (y constant)
        for (int yi = 0; yi < ySteps; yi++) {
            for (int x = 0; x < xSteps - 1; x++) {
                //start vertex index
                indices[i++] = (short) (yi * xSteps + x);
                //end vertex index
                indices[i++] = (short) (yi * xSteps + x + 1);
            }
        }

        // Vertical grid lines (x constant)
        for (int xi = 0; xi < xSteps; xi++) {
            for (int yi = 0; yi < ySteps - 1; yi++) {
                indices[i++] = (short) (yi * xSteps + xi);
                indices[i++] = (short) ((yi + 1) * xSteps + xi);
            }
        }

        indicesCount = indices.length;

        bb = ByteBuffer.allocateDirect(indices.length * BYTES_PER_SHORT);
        bb.order(ByteOrder.nativeOrder());
        ShortBuffer indexBuffer = bb.asShortBuffer();
        indexBuffer
                .put(indices)
                .position(0);

        GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER,
                indices.length * BYTES_PER_SHORT,
                indexBuffer,
                GLES20.GL_STATIC_DRAW
        );

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

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

    public void draw(float[] viewmtx, float[] projmtx, float[] colorCorrectionRgba) {
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

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId);

        GLES20.glLineWidth(15);
        GLES20.glDrawElements(GLES20.GL_LINES, indicesCount, GLES20.GL_UNSIGNED_SHORT, 0);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);

        ShaderUtil.checkGLError(TAG, "After draw");
    }

}
