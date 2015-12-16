package com.amg.rubik;

import android.util.Log;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;
import com.amg.rubik.Rotation.Direction;
import com.amg.rubik.Rotation.Axis;

/**
 * Created by amar on 9/12/15.
 */
public class RubiksCube3x3x3 extends RubiksCube {

    enum SolveState {
        None,
        FirstFaceCross,
        FirstFaceCorners,
        MiddleFace,
        LastFaceCross,
        LastFaceCrossAlign,
        LastFaceCorners,
        LastFaceCornerAlign
    }

    private static final int SIZE = 3;

    private static final int INNER = 0;
    private static final int MIDDLE = 1;
    private static final int OUTER = 2;

    private static final int FIRST_ROW_LEFT = 0;
    private static final int FIRST_ROW_CENTER = 1;
    private static final int FIRST_ROW_RIGHT = 2;
    private static final int MID_ROW_LEFT = 3;
    private static final int CENTER = 4;
    private static final int MID_ROW_RIGHT = 5;
    private static final int LAST_ROW_LEFT = 6;
    private static final int LAST_ROW_MIDDLE = 7;
    private static final int LAST_ROW_RIGHT = 8;

    // Middle row in Y axis starts from mid-front-left and continues anticlockwise
    private static final int EDGE_MIDDLE_FRONT_LEFT = 0;
    private static final int EDGE_MIDDLE_FRONT_RIGHT = 2;
    private static final int EDGE_MIDDLE_RIGHT_BACK = 4;
    private static final int EDGE_MIDDLE_LEFT_BACK = 6;

    private static final int CORNER_INDEX_FRONT_RIGHT = 0;
    private static final int CORNER_INDEX_RIGHT_BACK = 1;
    private static final int CORNER_INDEX_BACK_LEFT = 2;
    private static final int CORNER_INDEX_LEFT_FRONT = 3;

    // bottom row numbering is similar to front face after a clockwise rotation around X axis
    private static final int EDGE_BOTTOM_NEAR = FIRST_ROW_CENTER;
    private static final int EDGE_BOTTOM_RIGHT = MID_ROW_RIGHT;
    private static final int EDGE_BOTTOM_LEFT = MID_ROW_LEFT;
    private static final int EDGE_BOTTOM_FAR = LAST_ROW_MIDDLE;

    // top row numbering is similar to front face after a counter clockwise rotation around X axis
    private static final int EDGE_TOP_FAR = FIRST_ROW_CENTER;
    private static final int EDGE_TOP_NEAR = LAST_ROW_MIDDLE;
    private static final int EDGE_TOP_LEFT = MID_ROW_LEFT;
    private static final int EDGE_TOP_RIGHT = MID_ROW_RIGHT;

    private SolveState solveState = SolveState.None;

    private int mTopColor = 0;
    private int mBottomColor = 0;

    public RubiksCube3x3x3() {
        super(SIZE);
    }

    @Override
    public int solve() {
        if (mState == CubeState.TESTING) {
            mState = CubeState.IDLE;
        }
        if (mState != CubeState.IDLE) {
            sendMessage("Invalid state to solve: " + mState);
            return -1;
        }
        mState = CubeState.SOLVING;
        startSolving();
        return 0;
    }

    private void startSolving() {
        solveState = SolveState.FirstFaceCross;
        mTopColor = mTopSquares.get(CENTER).mColor;
        mBottomColor = mBottomSquares.get(CENTER).mColor;
        sendMessage("Top is " + mTopSquares.get(CENTER).colorName() +
                " and bottom is " + mBottomSquares.get(CENTER).colorName());
        firstFaceCross();
    }

    private void firstFaceCross() {
        ArrayList<Square>[] sideFaces = new ArrayList[]{
                mBackSquares, mLeftSquares, mRightSquares, mFrontSquares
        };

        // TODO: Handle already aligned pieces here
        for (int i = EDGE_TOP_NEAR; i > 0; i--) {
            if (i % 2 == 0) {
                continue;
            }
            ArrayList<Square> sideFace = sideFaces[i / 2];
            if (mTopSquares.get(i).mColor == mTopColor &&
                    sideFace.get(FIRST_ROW_CENTER).mColor == sideFace.get(CENTER).mColor) {
                continue;
            }

            // If the other color in the missing edge is not the front-color, rotate the cube
            // until it becomes so.
            if (i != EDGE_TOP_NEAR) {
                Direction dir = (i == EDGE_TOP_LEFT) ?
                        Direction.COUNTER_CLOCKWISE : Direction.CLOCKWISE;
                Algorithm algo = Algorithm.rotateWhole(Axis.Y_AXIS, dir, SIZE,
                        i == EDGE_TOP_FAR ? 2 : 1);
                setAlgo(algo);
            } else {
                fixFirstFaceEdge(mTopColor, sideFace.get(CENTER).mColor);
            }
            return;
        }

        sendMessage("Top cross is done, cutting corners now");
        solveState = SolveState.FirstFaceCorners;
        firstFaceCorners();
    }

    private void fixFirstFaceEdge(int topColor, int sideColor) {
        int[] colors = new int[] {topColor, sideColor};
        int row = 0, pos = -1;
        for (row = 0; row < SIZE; row++) {
            pos = findPieceOnFace(mYaxisFaceList.get(row), colors);
            if (pos >= 0) {
                break;
            }
        }

        Log.w(tag, "Found " +
                Square.getColorName(topColor) + '-' +
                Square.getColorName(sideColor) + " at " + row + "-" + pos);

        // White on bottom face
        if (row == INNER && mBottomSquares.get(pos).mColor == topColor) {
            firstFaceEdge_fromBottomFace(pos);
        } else if (row == INNER) {
            firstFaceEdge_fromLowerLayer(pos);
        } else if (row == MIDDLE) {
            firstFaceEdge_fromMiddleLayer(pos);
        } else {
            firstFaceEdge_fromTopLayer(pos);
        }
    }

    private void firstFaceEdge_fromTopLayer(final int pos) {
        sendMessage("Edge piece from top layer");
        Algorithm algo = new Algorithm();
        ArrayList<Rotation> middleRotations;
        Rotation rot = null;
        Square topColoredSquare = getSquareByColor(mYaxisFaceList, OUTER, pos, mTopColor);


        if (pos == EDGE_TOP_FAR || pos == EDGE_TOP_NEAR) {
            int faceIndex = topColoredSquare.getFace() == FACE_TOP ?
                    FACE_RIGHT : topColoredSquare.getFace();
            rot = new Rotation(Axis.Z_AXIS,
                    Direction.CLOCKWISE,
                    pos == EDGE_TOP_FAR ? INNER : OUTER);
            algo.addStep(rot);
            middleRotations = middleEdgeToTopEdge(
                    pos == EDGE_TOP_FAR ? EDGE_MIDDLE_RIGHT_BACK : EDGE_MIDDLE_FRONT_RIGHT,
                    mTopColor, faceIndex);
        } else {
            int faceIndex = topColoredSquare.getFace() == FACE_TOP ?
                    FACE_FRONT : topColoredSquare.getFace();
            rot = new Rotation(Axis.X_AXIS,
                    Direction.COUNTER_CLOCKWISE,
                    pos == EDGE_TOP_LEFT ? INNER : OUTER);
            algo.addStep(rot);
            middleRotations = middleEdgeToTopEdge(pos == EDGE_TOP_LEFT ?
                    EDGE_MIDDLE_FRONT_LEFT : EDGE_MIDDLE_FRONT_RIGHT, mTopColor, faceIndex);
        }

        for (int i = 0; i < middleRotations.size(); i++) {
            algo.addStep(middleRotations.get(i));
        }

        setAlgo(algo);
    }

    private static ArrayList<Rotation> middleEdgeToTopEdge(int middlePos, int topColor, int faceWithTopColor) {
        ArrayList<Rotation> rotations = new ArrayList<>();

        switch (middlePos) {
            case EDGE_MIDDLE_FRONT_LEFT:
                assert faceWithTopColor == FACE_FRONT || faceWithTopColor == FACE_LEFT;
                if (faceWithTopColor == FACE_FRONT) {
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.X_AXIS, Direction.CLOCKWISE, INNER));
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                } else {
                    rotations.add(new Rotation(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER));
                }
                break;

            case EDGE_MIDDLE_FRONT_RIGHT:
                assert faceWithTopColor == FACE_FRONT || faceWithTopColor == FACE_RIGHT;
                if (faceWithTopColor == FACE_FRONT) {
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.X_AXIS, Direction.CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER));
                } else {
                    rotations.add(new Rotation(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                }
                break;

            case EDGE_MIDDLE_RIGHT_BACK:
                assert faceWithTopColor == FACE_RIGHT || faceWithTopColor == FACE_BACK;
                if (faceWithTopColor == FACE_BACK) {
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER));
                } else {
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, INNER));
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                }
                break;

            case EDGE_MIDDLE_LEFT_BACK:
                assert faceWithTopColor == FACE_LEFT || faceWithTopColor == FACE_BACK;
                if (faceWithTopColor == FACE_BACK) {
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER));
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                } else {
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.Z_AXIS, Direction.CLOCKWISE, INNER));
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                    rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
                }
                break;
        }
        return rotations;
    }

    Square getSquareByColor(ArrayList<ArrayList<Piece>> faceList, int index, int pos, int color) {
        Piece piece = faceList.get(index).get(pos);
        for (Square sq : piece.mSquares) {
            if (sq.mColor == color) {
                return sq;
            }
        }
        throw new InvalidParameterException("Square not found: Index " + index +
                ", pos " + pos + ", color " + color);
    }

    private void firstFaceEdge_fromMiddleLayer(int pos) {
        sendMessage("Edge piece from middle layer");
        Square topColorSquare = getSquareByColor(mYaxisFaceList, MIDDLE, pos, mTopColor);
        int faceIndex = topColorSquare.getFace();

        ArrayList<Rotation> rotations = middleEdgeToTopEdge(pos, mTopColor, faceIndex);
        Algorithm algo = new Algorithm();
        for (int i = 0; i < rotations.size(); i++) {
            algo.addStep(rotations.get(i));
        }
        setAlgo(algo);
    }

    /**
     * pos: position of desired piece in bottom face.
     * The piece should have non-white on bottom face
     */
    private void firstFaceEdge_fromLowerLayer(int pos) {
        sendMessage("Edge piece from lower layer");
        Algorithm algorithm = new Algorithm();

        // the white should be on one of the sides, not front or back
        if (pos == EDGE_BOTTOM_NEAR || pos == EDGE_BOTTOM_FAR) {
            algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, INNER);
        }

        if (pos <= EDGE_BOTTOM_LEFT) {
            algorithm.addStep(Axis.X_AXIS, Direction.CLOCKWISE, INNER);
            algorithm.addStep(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER);
            if (mTopSquares.get(EDGE_TOP_LEFT).mColor == mTopColor &&
                    mLeftSquares.get(FIRST_ROW_CENTER).mColor == mLeftSquares.get(CENTER).mColor) {
                algorithm.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER);
            }
        } else {
            algorithm.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER);
            algorithm.addStep(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER);
            if (mTopSquares.get(EDGE_TOP_RIGHT).mColor == mTopColor &&
                    mRightSquares.get(FIRST_ROW_CENTER).mColor == mRightSquares.get(CENTER).mColor) {

                algorithm.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER);
            }
        }
        setAlgo(algorithm);
    }

    /**
     * pos: position of desired piece in bottom face.
     * The piece should have white on bottom face
     */
    private void firstFaceEdge_fromBottomFace(int pos) {
        Algorithm algo = new Algorithm();
        sendMessage("Edge piece from bottom face");

        /**
         * Piece is not aligned yet.
         * Rotate bottom face
         * */
        if (pos != EDGE_BOTTOM_NEAR) {
            Direction dir = pos == EDGE_BOTTOM_LEFT ?
                    Direction.COUNTER_CLOCKWISE : Direction.CLOCKWISE;
            Rotation rot = new Rotation(Axis.Y_AXIS, dir, INNER);
            algo.addStep(rot);
            if (pos == EDGE_BOTTOM_FAR) {
                algo.addStep(rot);
            }
        }
        // Front face twice
        Rotation rot = new Rotation(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER);
        algo.addStep(rot);
        algo.addStep(rot);
        setAlgo(algo);
    }

    /**
     * Corners
     * */

    private boolean isCornerAligned(Piece piece) {
        if (piece.mSquares.size() != 3) throw new AssertionError();
        for (Square sq : piece.mSquares) {
            if (sq.mColor != mAllFaces[sq.getFace()].get(CENTER).mColor) {
                return false;
            }
        }
        return true;
    }

    private void firstFaceCorners() {
        int[] corners = new int[]{
                LAST_ROW_RIGHT, LAST_ROW_LEFT, FIRST_ROW_LEFT, FIRST_ROW_RIGHT
        };
        /**
         * Look for any corners in the lower layer with white facing sideways (not bottom).
         * */
        for (int i = 0; i < corners.length; i++) {
            Piece cornerPiece = mYaxisFaceList.get(INNER).get(corners[i]);
            Square topColoredSquare = cornerPiece.getSquare(mTopColor);
            if (topColoredSquare == null) continue;
            if (topColoredSquare.getFace() == FACE_BOTTOM) continue;
            sendMessage("Found " + cornerPiece + " at " + corners[i]);
            firstFaceCorner(corners[i]);
            return;
        }

        Log.w(tag, "No whites in the lower layer. Bring up whites from bottom face");

        for (int i = 0; i < corners.length; i++) {
            Piece cornerPiece = mYaxisFaceList.get(INNER).get(corners[i]);
            Square topColoredSquare = cornerPiece.getSquare(mTopColor);
            if (topColoredSquare == null) continue;
            if (topColoredSquare.getFace() != FACE_BOTTOM) {
                throw new AssertionError("white faces " +
                        topColoredSquare.getFace() + " at " + corners[i]);
            }
            sendMessage("White faces down in " + cornerPiece + " at " + corners[i]);
            firstFaceCornerWhiteOnBottom(corners[i]);
            return;
        }

        Log.w(tag, "Look for whites in top layer");
        for (int i = 0; i < corners.length; i++) {
            Piece cornerPiece = mYaxisFaceList.get(OUTER).get(corners[i]);
            if (isCornerAligned(cornerPiece)) {
                continue;
            }
            sendMessage("unaligned at top row " + cornerPiece + " at " + corners[i]);
            firstFaceCornerFromTopLayer(corners[i]);
            return;
        }

        sendMessage("We have a perfect first layer..!");
        solveState = SolveState.MiddleFace;
    }

    private static int corner2index(int face, int corner) {
        if (face == FACE_BOTTOM) {
            switch (corner) {
                case FIRST_ROW_RIGHT:
                    return CORNER_INDEX_FRONT_RIGHT;
                case LAST_ROW_RIGHT:
                    return CORNER_INDEX_RIGHT_BACK;
                case LAST_ROW_LEFT:
                    return CORNER_INDEX_BACK_LEFT;
                case FIRST_ROW_LEFT:
                    return CORNER_INDEX_LEFT_FRONT;
                default:
                    throw new InvalidParameterException("Invalid corner " + corner);
            }
        } else if (face == FACE_TOP) {
            switch (corner) {
                case FIRST_ROW_LEFT:
                    return CORNER_INDEX_BACK_LEFT;
                case FIRST_ROW_RIGHT:
                    return CORNER_INDEX_RIGHT_BACK;
                case LAST_ROW_LEFT:
                    return CORNER_INDEX_LEFT_FRONT;
                case LAST_ROW_RIGHT:
                    return CORNER_INDEX_FRONT_RIGHT;
                default:
                    throw new InvalidParameterException("Invalid corner " + corner);
            }
        } else {
            throw new InvalidParameterException("not implemented for " + face);
        }
    }

    private void firstFaceCornerFromTopLayer(int corner) {
        Algorithm algorithm = new Algorithm();
        Piece piece = mYaxisFaceList.get(OUTER).get(corner);
        if (piece.getType() != Piece.PieceType.CORNER) throw new AssertionError();
        final int topColor = mTopSquares.get(CENTER).mColor;
        int topColorFace = -1;
        for (Square sq : piece.mSquares) {
            if (sq.mColor == topColor) {
                topColorFace = sq.getFace();
                continue;
            }
        }

        int desiredCornerIndex = CORNER_INDEX_FRONT_RIGHT;
        int currentCornerIndex = corner2index(FACE_TOP, corner);
        int delta = Math.abs(currentCornerIndex - desiredCornerIndex);
        Log.w(tag, "corners " + currentCornerIndex + ", " + desiredCornerIndex);

        if (desiredCornerIndex != currentCornerIndex) {
            /**
             * Bring the desired corner to front-right. Make sure that orientation of white
             * is updated to reflect this
             * */
            Direction direction = (currentCornerIndex == CORNER_INDEX_LEFT_FRONT) ?
                    Direction.COUNTER_CLOCKWISE : Direction.CLOCKWISE;
            algorithm.addStep(Axis.Y_AXIS, direction, 0, SIZE);
            if (topColorFace != FACE_TOP) {
                topColorFace += direction == Direction.COUNTER_CLOCKWISE ? 1 : -1;
            }
            if (currentCornerIndex == CORNER_INDEX_BACK_LEFT) {
                algorithm.repeatLastStep();
                if (topColorFace != FACE_TOP) {
                    topColorFace += direction == Direction.COUNTER_CLOCKWISE ? 1 : -1;
                }
            }
        }

        topColorFace = (topColorFace + CUBE_SIDES) % CUBE_SIDES;

        if (topColorFace == FACE_FRONT || topColorFace == FACE_TOP) {
            algorithm.addStep(Axis.Z_AXIS, Direction.CLOCKWISE, OUTER);
            algorithm.addStep(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, INNER);
            algorithm.addStep(Axis.Z_AXIS, Direction.COUNTER_CLOCKWISE, OUTER);
        } else if (topColorFace == FACE_RIGHT) {
            algorithm.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER);
            algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, INNER);
            algorithm.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER);
        } else {
            throw new AssertionError("white should not be facing " + topColorFace);
        }

        setAlgo(algorithm);
    }

    private void firstFaceCornerWhiteOnBottom(int corner) {
        Algorithm algorithm = new Algorithm();
        Direction direction;
        Piece piece = mYaxisFaceList.get(INNER).get(corner);
        if (piece.getType() != Piece.PieceType.CORNER) throw new AssertionError();
        final int topColor = mTopSquares.get(CENTER).mColor;
        int sideColor1 = -1;
        int sideColor2 = -1;
        for (Square sq : piece.mSquares) {
            if (sq.mColor == topColor) {
                if (sq.getFace() != FACE_BOTTOM) throw new AssertionError();
                continue;
            }
            if (sideColor1 == -1) {
                sideColor1 = sq.mColor;
            } else if (sideColor2 == -1) {
                sideColor2 = sq.mColor;
            }
        }

        int face1 = getColorFace(sideColor1);
        int face2 = getColorFace(sideColor2);
        int desiredCorner = FIRST_ROW_LEFT;

        if (face1 == FACE_BACK || face2 == FACE_BACK) {
            desiredCorner = LAST_ROW_LEFT;
        }

        if (face1 == FACE_RIGHT || face2 == FACE_RIGHT) {
            desiredCorner += 2;
        }

        int currentCornerIndex = corner2index(FACE_BOTTOM, corner);
        int desiredCornerIndex = corner2index(FACE_BOTTOM, desiredCorner);
        int delta = Math.abs(currentCornerIndex - desiredCornerIndex);
        Log.w(tag, "corners " + currentCornerIndex + ", " + desiredCornerIndex);

        if (desiredCornerIndex != CORNER_INDEX_FRONT_RIGHT) {
            // Bring the desired corner to front-right
            direction = (desiredCorner == FIRST_ROW_LEFT) ?
                    Direction.COUNTER_CLOCKWISE : Direction.CLOCKWISE;
            algorithm.addStep(Axis.Y_AXIS, direction, 0, SIZE);
            if (desiredCorner == LAST_ROW_LEFT) {
                algorithm.addStep(Axis.Y_AXIS, direction, 0, SIZE);
            }
        }

        // Rotate lower layer to bring the piece to front-right
        direction = desiredCornerIndex < currentCornerIndex ?
                Direction.CLOCKWISE : Direction.COUNTER_CLOCKWISE;
        if (delta == 3) {
            delta = 1;
            direction = direction == Direction.CLOCKWISE ?
                    Direction.COUNTER_CLOCKWISE : Direction.CLOCKWISE;
        }
        for (int i = 0; i < delta; i++) {
            algorithm.addStep(Axis.Y_AXIS, direction, INNER);
        }

        algorithm.addStep(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER);
        algorithm.addStep(Axis.Y_AXIS, Direction.CLOCKWISE, INNER);
        algorithm.repeatLastStep();
        algorithm.addStep(Axis.X_AXIS, Direction.CLOCKWISE, OUTER);
        setAlgo(algorithm);
    }

    private void firstFaceCorner(int corner) {
        Piece piece = mYaxisFaceList.get(INNER).get(corner);
        assert piece.getType() == Piece.PieceType.CORNER;
        int topColor = mTopSquares.get(CENTER).mColor;
        int topColorFace = -1;
        int sideColor = -1;
        int bottomColor = -1;
        int sideFace = -1;
        for (Square sq : piece.mSquares) {
            if (sq.mColor == topColor) {
                topColorFace = sq.getFace();
                Log.w(tag, "white faces " + topColorFace);
                if (topColorFace == FACE_BOTTOM) throw new AssertionError();
                continue;
            }
            if (sq.getFace() == FACE_BOTTOM) {
                bottomColor = sq.mColor;
            } else {
                sideColor = sq.mColor;
                sideFace = sq.getFace();
            }
        }
        int sideColorCenterFace = getColorFace(sideColor);
        Log.w(tag, Square.getColorName(sideColor) + " center is at " + sideColorCenterFace +
            ", face of that color on piece " + sideFace);
        assert sideColorCenterFace <= FACE_LEFT;
        ArrayList<Rotation> rotations = bringColorToFront(sideColor);
        Log.w(tag, Square.getColorName(sideColor) + " should be at front after " + rotations.size());

        int count = Math.abs(sideColorCenterFace - sideFace);
        Direction direction;
        direction = sideColorCenterFace > sideFace ?
                Direction.COUNTER_CLOCKWISE : Direction.CLOCKWISE;

        Log.w(tag, "count " + count + " direction " + direction);

        if (count == 3) {
            count = 1;
            direction = direction == Direction.CLOCKWISE ?
                    Direction.COUNTER_CLOCKWISE : Direction.CLOCKWISE;
        }

        for (int i = 0; i < count; i++) {
            rotations.add(new Rotation(Axis.Y_AXIS, direction, INNER));
        }

        topColorFace -= sideFace;
        topColorFace = (topColorFace + CUBE_SIDES) % CUBE_SIDES;

        Log.w(tag, "white should now be at face " + topColorFace);

        if (topColorFace == FACE_RIGHT) {
            rotations.add(new Rotation(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, OUTER));
            rotations.add(new Rotation(Axis.Y_AXIS, Direction.CLOCKWISE, INNER));
            rotations.add(new Rotation(Axis.X_AXIS, Direction.CLOCKWISE, OUTER));
        } else if (topColorFace == FACE_LEFT) {
            rotations.add(new Rotation(Axis.X_AXIS, Direction.COUNTER_CLOCKWISE, INNER));
            rotations.add(new Rotation(Axis.Y_AXIS, Direction.COUNTER_CLOCKWISE, INNER));
            rotations.add(new Rotation(Axis.X_AXIS, Direction.CLOCKWISE, INNER));
        } else {
            throw new AssertionError("topColorFace should be left or right, not: " + topColorFace);
        }

        Algorithm algorithm = new Algorithm(rotations);
        setAlgo(algorithm);
    }

    private int getColorFace(int color) {
        for (int i = 0; i < FACE_COUNT; i++) {
            if (mAllFaces[i].get(CENTER).mColor == color) {
                return i;
            }
        }
        throw new InvalidParameterException("Color not found: " + color);
    }

    private int findPieceOnFace(ArrayList<Piece> face, int[] colors) {
        Arrays.sort(colors);
        for (int i = 0; i < face.size(); i++) {
            Piece piece = face.get(i);
            if (piece.mSquares.size() != colors.length)
                continue;
            int[] pieceColors = new int[piece.mSquares.size()];
            for (int j = 0; j < pieceColors.length; j++) {
                pieceColors[j] = piece.mSquares.get(j).mColor;
            }
            Arrays.sort(pieceColors);
            boolean found = true;
            for (int j = 0; j < colors.length; j++) {
                if (colors[j] != pieceColors[j]) {
                    found = false;
                    break;
                }
            }
            if (found)
                return i;
        }
        return -1;
    }

    @Override
    void updateAlgo() {
        if (mState != CubeState.SOLVING)
            return;

        switch (solveState) {
            case FirstFaceCross:
                firstFaceCross();
                break;

            case FirstFaceCorners:
                firstFaceCorners();
                break;

            default:
                mState = CubeState.IDLE;
                sendMessage("Thats all I can do now");
                break;
        }
    }

    private ArrayList<Rotation> bringColorToFront(int color) {
        assert color >= 0 && color <= FACE_COUNT;
        ArrayList<Rotation> rotations = new ArrayList<>();
        if (color == mFrontSquares.get(CENTER).mColor) {
            return rotations;
        }
        Axis axis = Axis.Y_AXIS;
        Direction dir = Direction.CLOCKWISE;
        if (color == mTopSquares.get(CENTER).mColor) {
            axis = Axis.X_AXIS;
            dir = Direction.COUNTER_CLOCKWISE;
        } else if (color == mBottomSquares.get(CENTER).mColor) {
            axis = Axis.X_AXIS;
        } else if (color == mLeftSquares.get(CENTER).mColor) {
            dir = Direction.COUNTER_CLOCKWISE;
        }
        rotations.add(new Rotation(axis, dir, 0, SIZE));
        if (color == mBackSquares.get(CENTER).mColor) {
            rotations.add(new Rotation(axis, dir, 0, SIZE));
        }
        return rotations;
    }
}