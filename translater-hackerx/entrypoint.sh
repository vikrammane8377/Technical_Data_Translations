#!/bin/sh

# Copy the JAR file to the output directory
cp /app/target/*.jar /output/

# Keep the container running to inspect the results (optional)
tail -f /dev/null

