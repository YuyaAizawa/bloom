package com.lethe_river.bloom;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * {@link BloomFilter}を生成するクラス.
 * 
 * @see {@link Bloom32}, {@link Bloom64}
 * @author YuyaAizawa
 *
 * @param <T>
 */
public class BloomConfig<T> {
	private final List<Function<T, Integer>> hashes;
	private final int filterBytes;
	private final int hashFilter;
	
	/**
	 * 指定した関数をハッシュとして用いるBloomConfigを返す
	 * @param hashes 利用するハッシュ関数
	 * @param filterBytes BloomFilterのbyte長
	 */
	public BloomConfig(List<Function<T, Integer>> hashes, int filterBytes) {
		
		if(filterBytes < 1 || filterBytes % 4 != 0) {
			throw new IllegalArgumentException("filterBytes must be multiple of 4");
			// FIXME
		}
		
		this.hashes = hashes;
		this.filterBytes = filterBytes;
		this.hashFilter = filterBytes*8-1;
	}
	
	/**
	 * 指定した関数をハッシュとして用いるBloomConfigを返す
	 * @param hash 利用するハッシュ関数
	 * @param filterBytes BloomFilterのbyte長
	 */
	public BloomConfig(Function<T, Integer> hash, int filterBytes) {
		this(Collections.singletonList(hash), filterBytes);
	}
	
	/**
	 * 空のBloomFilterを返す
	 * @return 空のBloomFilter
	 */
	public BloomFilter<T> empty() {
		return new BloomFilter<T>(this, new int[filterBytes/4]);
	}
	
	/**
	 * 指定したオブジェクトを含むBloomFilterを返す．
	 * @param t
	 * @return
	 */
	public BloomFilter<T> getFilter(T t) {
		int[] filter = new int[filterBytes/4];
		for(Function<T, Integer> hash:hashes) {
			int indexBit = hash.apply(t) & hashFilter;
			filter[indexBit/32] |= (1 << (indexBit%32));
		}
		return new BloomFilter<>(this, filter);
	}
	
	/**
	 * コレクションに含まれるオブジェクトを全て含むBloomFilterを返す．
	 * @param ts
	 * @return
	 */
	public final BloomFilter<T> getFilter(Collection<T> ts) {
		int[] filter = new int[filterBytes/4];
		
		for(T t:ts) {
			for(Function<T, Integer> hash:hashes) {
				int indexBit = hash.apply(t) & hashFilter;
				filter[indexBit/32] |= (1 << (indexBit%32));
			}
		}
		return new BloomFilter<>(this, filter);
	}
	
	// ここから下は非常に読みにくい
	
	/**
	 * SHA-256をハッシュ関数としたBloomConfigを作成する．
	 * 
	 * @param hashNum 利用するハッシュ関数の数(1~8)
	 * @param filterBytes
	 * @param salt SHA-256に用いるソルト値
	 * @return
	 */
	public static <T> BloomConfig<T> withSHA256(int hashNum, int filterBytes, byte[] salt) {
		
		if(hashNum < 1 || 8 < hashNum) {
			throw new IllegalArgumentException();
		}
		
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			
			byte[] buff = new byte[32];
			ByteBuffer bb = ByteBuffer.wrap(buff);
			
			
			@SuppressWarnings("unchecked")
			List<Function<T, Integer>> hashFuns = IntStream.rangeClosed(0, hashNum-1)
			.mapToObj(i -> i == 0 ? (Function<T, Integer>)
					t -> {
						withSHA256SubCon(md, bb, salt).accept(t);
						return withSHA256SubFun(bb, 0).apply(t);
					} : (Function<T, Integer>)withSHA256SubFun(bb, i*4))
			.collect(Collectors.toList());
			
			return new BloomConfig<>(hashFuns, filterBytes);
		} catch (NoSuchAlgorithmException e1) {
			throw new RuntimeException(e1);
		}
	}
	
	public static <T> BloomConfig<T> withSHA256(int hashNum, int filterBytes) {
		return withSHA256(hashNum, filterBytes, new byte[]{});
	}
	
	private static <T> Consumer<T> withSHA256SubCon(MessageDigest md, ByteBuffer bb, byte[] salt) {
		return t -> {
			md.update(salt);
			try (ObjectOutputStream oos = new Mdoos(md)) {
				oos.writeObject(t);
			} catch (IOException e) {
				throw new RuntimeException();
			}
			bb.position(0);
			bb.put(md.digest());
		};
	}
	
	private static class Mdoos extends ObjectOutputStream {
		
		protected Mdoos(MessageDigest md) throws IOException, SecurityException {
			super(new OutputStream() {
				@Override
				public void write(int b) throws IOException {
					md.update((byte)(0xff & b));
				}
			});
		}
		
	}
	
	private static <T> Function<T, Integer> withSHA256SubFun(ByteBuffer bb, int startByte) {
		return t -> {
			byte[] b = bb.array();
			return
					((b[startByte+3] & 0xff) << 24) |
					((b[startByte+2] & 0xff) << 16) |
					((b[startByte+1] & 0xff) <<  8) |
					(b[startByte] & 0xff);
		};
	}
}
