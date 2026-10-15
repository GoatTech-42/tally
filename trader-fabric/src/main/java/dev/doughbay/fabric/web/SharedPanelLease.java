package dev.doughbay.fabric.web;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** One panel host per shared configuration folder. The OS releases it on process exit. */
final class SharedPanelLease implements AutoCloseable {
    private final FileChannel channel;
    private final FileLock lock;
    private SharedPanelLease(FileChannel channel, FileLock lock) { this.channel=channel; this.lock=lock; }
    static SharedPanelLease acquire(Path directory) throws IOException {
        FileChannel channel=FileChannel.open(directory.resolve("web-panel.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try {
            FileLock lock;
            try { lock=channel.tryLock(); } catch(OverlappingFileLockException e) { lock=null; }
            if(lock==null) { channel.close(); return null; }
            return new SharedPanelLease(channel,lock);
        } catch(IOException|RuntimeException e) { channel.close(); throw e; }
    }
    @Override public void close() throws IOException {
        try { lock.release(); } finally { channel.close(); }
    }
}
