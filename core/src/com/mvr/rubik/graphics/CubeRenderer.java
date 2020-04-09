package com.mvr.rubik.graphics;

import com.mvr.rubik.cube.Square;

public interface CubeRenderer {

    /**
     * Draw square without any rotation
     * */
    void drawSquare(Square square);

    /**
     * Rotate the square by angleDegrees along the axis (x, y, z)
     * */
    void drawSquare(Square square, float angleDegrees, float x, float y, float z);
}
