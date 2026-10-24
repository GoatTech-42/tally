package dev.doughbay.fabric.automation;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Bounded purchases per shelf cycle. Persist the purchase reservation before sending a bid. */
final class ReserveCycles {
    private final Map<String,Integer> purchases = new TreeMap<>();
    private Path path;
    private boolean readable = true;

    void load(Path path) throws IOException {
        this.path = path;
        purchases.clear();
        readable = false;
        if (path != null && Files.exists(path)) {
            for (String line : Files.readAllLines(path)) {
                int split = line.lastIndexOf('=');
                if (split <= 0) throw new IOException("Invalid reserve cycle record");
                int count;
                try { count = Integer.parseInt(line.substring(split+1)); }
                catch (NumberFormatException e) { throw new IOException("Invalid reserve cycle count",e); }
                if (count < 1 || count > 90) throw new IOException("Invalid reserve cycle count");
                if (purchases.putIfAbsent(line.substring(0,split),count) != null)
                    throw new IOException("Duplicate reserve cycle record");
            }
        }
        readable = true;
    }

    boolean mayPlace(String item, ReserveStock.Book book, long held, int batch) throws IOException {
        return mayPlace(item,book,held,batch,2);
    }

    boolean mayPlace(String item, ReserveStock.Book book, long held, int batch, int maxBatches) throws IOException {
        if (!readable || !book.valid() || held < 0 || maxBatches < 1 || maxBatches > 90) return false;
        if (book.units() == 0 && held == 0) {
            if (purchases.remove(item) != null) save();
        } else if (!purchases.containsKey(item)) {
            // An upgrade adopts existing exposure as a cycle already funded. Never buy over it.
            purchases.put(item,maxBatches);
            save();
        }
        return purchases.getOrDefault(item,0) < maxBatches && ReserveStock.mayPlace(book,held,batch,maxBatches);
    }

    void reservePurchase(String item) throws IOException {
        reservePurchase(item,2);
    }

    void reservePurchase(String item, int maxBatches) throws IOException {
        if (!readable || maxBatches < 1 || maxBatches > 90 || purchases.getOrDefault(item,0) >= maxBatches)
            throw new IOException("Reserve cycle is not ready");
        purchases.merge(item,1,Integer::sum);
        save();
    }

    private void save() throws IOException {
        try { write(); }
        catch (IOException e) { readable = false; throw e; }
    }

    private void write() throws IOException {
        if (path == null) throw new IOException("Reserve cycle storage is unavailable");
        Path temp = path.resolveSibling(path.getFileName()+".tmp");
        List<String> lines = purchases.entrySet().stream().map(e -> e.getKey()+"="+e.getValue()).toList();
        byte[] data = String.join("\n",lines).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (var channel = java.nio.channels.FileChannel.open(temp,StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)) {
            var bytes = java.nio.ByteBuffer.wrap(data);
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
    }
}
