cmake_minimum_required(VERSION 3.22.1)
project(gaze_link LANGUAGES CXX)

# OpenCV is loaded through the :opencv Android module. This JNI sample does not use it.
add_library(native-lib SHARED native-lib.cpp)
