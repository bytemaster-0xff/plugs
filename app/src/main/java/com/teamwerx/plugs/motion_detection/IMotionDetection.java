package com.teamwerx.plugs.motion_detection;

/**
 * Code adapted from: https://github.com/phishman3579/android-motion-detection
 * License: Apache-2.0
 *
 * This interface is used to represent a class that can detect motion
 *
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public interface IMotionDetection {

    /**
     * Get the previous image in integer array format
     *
     * @return int array of previous image.
     */
    public int[] getPrevious();

    /**
     * Detect motion.
     *
     * @param data
     *            integer array representing an image.
     * @param width
     *            Width of the image.
     * @param height
     *            Height of the image.
     * @return boolean True is there is motion.
     * @throws NullPointerException
     *             if data integer array is NULL.
     */
    public boolean detect(int[] data, int width, int height);
}