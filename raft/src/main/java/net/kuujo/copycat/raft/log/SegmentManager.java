/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat.raft.log;

import net.kuujo.copycat.io.Buffer;
import net.kuujo.copycat.io.FileBuffer;
import net.kuujo.copycat.io.serializer.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Log segment manager.
 * <p>
 * The segment manager keeps track of segments in a given {@link BufferedLog} and provides an interface to loading, retrieving,
 * and compacting those segments.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class SegmentManager implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(SegmentManager.class);
  private final LogConfig config;
  private final Serializer serializer;
  private NavigableMap<Long, Segment> segments = new ConcurrentSkipListMap<>();
  private Segment currentSegment;
  private long commitIndex;

  public SegmentManager(LogConfig config) {
    if (config == null)
      throw new NullPointerException("config cannot be null");
    this.config = config;
    this.serializer = config.getSerializer() != null ? config.getSerializer() : new Serializer();
    init();
  }

  /**
   * Initializes the log.
   */
  private void init() {
    // Load existing log segments from disk.
    for (Segment segment : loadSegments()) {
      segments.put(segment.descriptor().index(), segment);
    }

    // If a segment doesn't already exist, create an initial segment starting at index 1.
    if (!segments.isEmpty()) {
      currentSegment = segments.lastEntry().getValue();
    } else {
      currentSegment = createSegment(1, 1);
      segments.put(1l, currentSegment);
    }
  }

  /**
   * Checks whether the manager is open.
   */
  private void checkOpen() {
    if (currentSegment == null)
      throw new IllegalStateException("segment manager not open");
  }

  /**
   * Returns the log configuration.
   *
   * @return The log configuration.
   */
  public LogConfig config() {
    return config;
  }

  /**
   * Returns the current segment.
   *
   * @return The current segment.
   */
  public Segment currentSegment() {
    return currentSegment != null ? currentSegment : lastSegment();
  }

  /**
   * Resets the current segment, creating a new segment if necessary.
   */
  private void resetCurrentSegment() {
    Segment lastSegment = lastSegment();
    if (lastSegment != null) {
      currentSegment = lastSegment;
    } else {
      currentSegment = createSegment(1, 1);
    }
  }

  /**
   * Returns the first segment in the log.
   */
  public Segment firstSegment() {
    checkOpen();
    Map.Entry<Long, Segment> segment = segments.firstEntry();
    return segment != null ? segment.getValue() : null;
  }

  /**
   * Returns the last segment in the log.
   */
  public Segment lastSegment() {
    checkOpen();
    Map.Entry<Long, Segment> segment = segments.lastEntry();
    return segment != null ? segment.getValue() : null;
  }

  /**
   * Creates and returns the next segment.
   *
   * @return The next segment.
   */
  public Segment nextSegment() {
    checkOpen();
    Segment lastSegment = lastSegment();
    currentSegment = createSegment(lastSegment != null ? lastSegment.descriptor().id() + 1 : 1, currentSegment.lastIndex() + 1);
    segments.put(currentSegment.descriptor().index(), currentSegment);
    return currentSegment;
  }

  /**
   * Returns the collection of segments.
   *
   * @return An ordered collection of segments.
   */
  public Collection<Segment> segments() {
    return segments.values();
  }

  /**
   * Returns the segment for the given index.
   *
   * @param index The index for which to return the segment.
   */
  public Segment segment(long index) {
    checkOpen();
    // Check if the current segment contains the given index first in order to prevent an unnecessary map lookup.
    if (currentSegment != null && currentSegment.containsIndex(index))
      return currentSegment;

    // If the index is in another segment, get the entry with the next lowest first index.
    Map.Entry<Long, Segment> segment = segments.floorEntry(index);
    return segment != null ? segment.getValue() : null;
  }

  /**
   * Removes a segment.
   *
   * @param segment The segment to remove.
   */
  public void remove(Segment segment) {
    currentSegment = null;

    Map<Long, Segment> removalSegments = segments.tailMap(segment.descriptor().index());
    removalSegments.clear();
    for (Iterator<Segment> i = removalSegments.values().iterator(); i.hasNext();) {
      i.next().delete();
    }
    resetCurrentSegment();
  }

  /**
   * Creates a new segment.
   *
   * @param segmentId The segment ID.
   * @param segmentIndex The segment's effective first index.
   * @return The new segment.
   */
  public Segment createSegment(long segmentId, long segmentIndex) {
    return createSegment(segmentId, segmentIndex, 1, -1);
  }

  /**
   * Creates a new segment.
   */
  public Segment createSegment(long segmentId, long segmentIndex, long segmentVersion, long range) {
    File segmentFile = SegmentFile.createSegmentFile(config.getDirectory(), config.getName(), segmentId, segmentVersion);

    Buffer buffer = FileBuffer.allocate(segmentFile, 1024 * 1024, config.getMaxSegmentSize() + config.getMaxEntrySize() + SegmentDescriptor.BYTES);
    try (SegmentDescriptor descriptor = SegmentDescriptor.builder(buffer.slice(SegmentDescriptor.BYTES))
      .withId(segmentId)
      .withIndex(segmentIndex)
      .withRange(range)
      .withVersion(segmentVersion)
      .withMaxEntrySize(config.getMaxEntrySize())
      .withMaxSegmentSize(config.getMaxSegmentSize())
      .build()) {

      Segment segment = Segment.open(buffer.position(SegmentDescriptor.BYTES).slice(), serializer, descriptor, createIndex(segmentId, segmentVersion));
      LOGGER.debug("Created segment: {} ({})", descriptor.id(), segmentFile.getName());
      return segment;
    }
  }

  /**
   * Loads a segment.
   */
  public Segment loadSegment(long segmentId, long segmentVersion) {
    File file = SegmentFile.createSegmentFile(config.getDirectory(), config.getName(), segmentId, segmentVersion);
    try (SegmentDescriptor descriptor = new SegmentDescriptor(FileBuffer.allocate(file, SegmentDescriptor.BYTES))) {
      Buffer buffer = FileBuffer.allocate(file, 1024 * 1024, config.getMaxSegmentSize() + config.getMaxEntrySize() + SegmentDescriptor.BYTES);
      buffer = buffer.position(SegmentDescriptor.BYTES).slice();
      Segment segment = Segment.open(buffer, serializer, descriptor, createIndex(segmentId, segmentVersion));
      LOGGER.debug("Loaded segment: {} ({})", descriptor.id(), file.getName());
      return segment;
    }
  }

  /**
   * Creates a segment index.
   */
  private OffsetIndex createIndex(long segmentId, long segmentVersion) {
    File file = SegmentFile.createIndexFile(config.getDirectory(), config.getName(), segmentId, segmentVersion);
    if (segmentVersion == 1) {
      return new OrderedOffsetIndex(FileBuffer.allocate(file, 1024 * 1024, config.getMaxEntriesPerSegment() * 4));
    } else {
      return new SearchableOffsetIndex(FileBuffer.allocate(file, 1024 * 1024, config.getMaxEntriesPerSegment() * 8));
    }
  }

  /**
   * Loads all segments from disk.
   *
   * @return A collection of segments for the log.
   */
  protected Collection<Segment> loadSegments() {
    // Ensure log directories are created.
    config.getDirectory().mkdirs();

    // Create a map of descriptors for each existing segment in the log. This is done by iterating through the log
    // directory and finding segment files for this log name. For each segment file, check the consistency of the file
    // by comparing versions and locked state in order to prevent lost data from failures during log compaction.
    Map<Long, SegmentDescriptor> descriptors = new HashMap<>();
    for (File file : config.getDirectory().listFiles(File::isFile)) {
      if (SegmentFile.isSegmentFile(file)) {
        SegmentFile segmentFile = new SegmentFile(file);
        try {
          // Check if the segment is a part of this log.
          if (segmentFile.name().equals(config.getName())) {
            // Create a new segment descriptor.
            SegmentDescriptor descriptor = new SegmentDescriptor(FileBuffer.allocate(file, SegmentDescriptor.BYTES));

            // Check that the descriptor matches the segment file metadata.
            if (descriptor.id() != segmentFile.id())
              throw new DescriptorException(String.format("Descriptor ID does not match filename ID: %s", segmentFile.file().getName()));
            if (descriptor.version() != segmentFile.version())
              throw new DescriptorException(String.format("Descriptor version does not match filename version: %s", segmentFile.file().getName()));

            // If a descriptor already exists for the segment, compare the descriptor versions.
            SegmentDescriptor existingDescriptor = descriptors.get(segmentFile.id());

            // If this segment's version is greater than the existing segment's version and the segment is locked then
            // overwrite it. The segment will be locked if all entries have been committed, e.g. after compaction.
            if (existingDescriptor == null) {
              LOGGER.debug("Found segment: {} ({})", descriptor.id(), segmentFile.file().getName());
              descriptors.put(descriptor.id(), descriptor);
            } else if (descriptor.version() > existingDescriptor.version() && descriptor.locked()) {
              LOGGER.debug("Replaced segment {} with newer version: {} ({})", existingDescriptor.id(), descriptor.version(), segmentFile.file().getName());
              descriptors.put(descriptor.id(), descriptor);
              existingDescriptor.close();
              existingDescriptor.delete();
            } else {
              descriptor.close();
            }
          }
        } catch (NumberFormatException e) {
          // It must not have been a valid segment file.
        }
      }
    }

    // Once we've constructed a map of the most recent descriptors, load the segments.
    List<Segment> segments = new ArrayList<>();
    for (SegmentDescriptor descriptor : descriptors.values()) {
      segments.add(loadSegment(descriptor.id(), descriptor.version()));
      descriptor.close();
    }
    return segments;
  }

  /**
   * Assigns a new segment list and deletes segments removed from the old segments map.
   */
  public void update(Collection<Segment> segments) {
    NavigableMap<Long, Segment> newSegments = new ConcurrentSkipListMap<>();
    segments.forEach(s -> newSegments.put(s.descriptor().index(), s));

    // Assign the new segments map and delete any segments that were removed from the map.
    NavigableMap<Long, Segment> oldSegments = this.segments;
    this.segments = newSegments;
    resetCurrentSegment();

    // Deletable segments are determined by whether the segment does not have a matching segment/version in the new segments.
    for (Segment oldSegment : oldSegments.values()) {
      Segment segment = this.segments.get(oldSegment.descriptor().index());
      if (segment == null || segment.descriptor().id() != oldSegment.descriptor().id() || segment.descriptor().version() > oldSegment.descriptor().version()) {
        LOGGER.debug("Deleting segment: {}-{}", oldSegment.descriptor().id(), oldSegment.descriptor().version());
        oldSegment.close();
        oldSegment.delete();
      }
    }
  }

  /**
   * Returns the commit index.
   */
  public long commitIndex() {
    return commitIndex;
  }

  /**
   * Commits all entries up to the given index.
   */
  public void commit(long index) {
    // If the commit index is greater than the current commit index than apply it to segments.
    // If the commit index is towards the beginning of a segment, it's possible that it could result in the commitment
    // of entries from the prior segment as well. We need to iterate through segments to ensure all uncommitted entries
    // are committed.
    if (index > commitIndex) {
      long nextIndex = index;
      Segment segment = segment(nextIndex);
      while (segment != null && segment.containsIndex(nextIndex) && segment.commitIndex() < nextIndex) {
        segment.commit(nextIndex);
        nextIndex = segment.firstIndex() - 1;
        segment = segment(nextIndex);
      }
      commitIndex = index;
    }
  }

  @Override
  public void close() {
    segments.values().forEach(s -> {
      LOGGER.debug("Closing segment: {}", s.descriptor().id());
      s.close();
    });
    segments.clear();
    currentSegment = null;
  }

  /**
   * Deletes all segments.
   */
  public void delete() {
    loadSegments().forEach(s -> {
      LOGGER.debug("Deleting segment: {}", s.descriptor().id());
      s.delete();
    });
  }

}