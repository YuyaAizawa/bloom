package com.lethe_river.bloom;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * {@link BloomFilter}を生成するクラス.
 * 
 * <p>フィルタの長さがintまたはlongであればそれぞれ{@link Bloom32}, {@link Bloom64}に簡易な実装がある．
 * 
 * @author YuyaAizawa
 *
 * @param <E> 要素の型
 */
public class BloomConfig<E> {
	
	// 使用するハッシュの数
	private final int hashNum;
	
	// フィルタの長さ(byte)
	private final int filterBytes;
	
	// 元となるハッシュ値を求めてhashTempに代入する
	private final Consumer<E> hashConsumer;
	private final byte[] hashTmp; // シフトの関係で3byte余計に
		
	// ひとつのフラグを立てるのに必要なハッシュの桁数(bit)
	private final int hashLength;
	private final int hashFilter;
	
	// hashConsumerを呼び出したらhashTmpに代入される状態にしておくこと
	BloomConfig(int hashNum, int filterBytes, int avalableHashBytes, Consumer<E> hashConsumer, byte[] hashTmp) {
		if(filterBytes < 1 || filterBytes % 4 != 0) {
			throw new IllegalArgumentException("filterBytes must be multiple of 4");
			// FIXME
		}
		
		this.hashLength = Integer.SIZE - Integer.numberOfLeadingZeros(filterBytes*8 - 1);
		if(hashLength * hashNum > avalableHashBytes * 8) {
			throw new IllegalArgumentException("Not enough hash!");
		}
		this.hashNum = hashNum;
		this.filterBytes = filterBytes;
		this.hashConsumer = hashConsumer;
		this.hashTmp = hashTmp;
		this.hashFilter = (1 << hashLength) - 1;
	}
	
	/**
	 * 指定した関数をハッシュの元として用いるBloomConfigを返す.
	 * @param hashFunction　元となるハッシュ関数
	 * @param hashNum　BloomFilterが利用するハッシュの数
	 * @param filterBytes　BloomFilterのbyte長
	 * @return BloomConfig
	 * @throws IllegalArgumentException 元となるハッシュ関数から十分な数のハッシュを作れないとき
	 */
	public static <E> BloomConfig<E> fromIntHash(Function<E, Integer> hashFunction, int hashNum, int filterBytes) {
		return fromIntHash(Collections.singletonList(hashFunction), hashNum, filterBytes);
	}
	
	/**
	 * 指定した複数の関数をハッシュの元として用いるBloomConfigを返す.
	 * @param hashFunctions　元となるハッシュ関数のList
	 * @param hashNum　BloomFilterが利用するハッシュの数
	 * @param filterBytes　BloomFilterのbyte長
	 * @return BloomConfig
	 * @throws IllegalArgumentException 元となるハッシュ関数から十分な数のハッシュを作れないとき
	 */
	public static <E> BloomConfig<E> fromIntHash(List<Function<E, Integer>> hashFunctions, int hashNum, int filterBytes) {
		int hashBits = Integer.SIZE - Integer.numberOfLeadingZeros(filterBytes*8 - 1);
		if(hashBits * hashNum > Integer.SIZE * hashFunctions.size()) {
			throw new IllegalArgumentException("No enough hashFunctions!");
		}
		
		byte[] hashTmp = new byte[hashFunctions.size()*Integer.BYTES+3];
		ByteBuffer bb = ByteBuffer.wrap(hashTmp);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		Consumer<E> hashConsumer = e -> {
			bb.position(0);
			hashFunctions.forEach(f -> {bb.putInt(f.apply(e));});
		};
		return new BloomConfig<>(hashNum, filterBytes, Integer.BYTES*hashFunctions.size(), hashConsumer, hashTmp);
	}
	
	/**
	 * 空のBloomFilterを返す
	 * @return 空のBloomFilter
	 */
	public BloomFilter<E> empty() {
		return new BloomFilter<E>(this, new int[filterBytes/Integer.BYTES]);
	}
	
	/**
	 * 指定したオブジェクトを含むBloomFilterを返す．
	 * @param t
	 * @return
	 */
	public BloomFilter<E> getFilter(E e) {
		int f[] = new int[filterBytes/Integer.BYTES];
		add(f, e);
		
		return new BloomFilter<>(this, f);
	}
	
	/**
	 * コレクションに含まれるオブジェクトを全て含むBloomFilterを返す．
	 * @param ts
	 * @return
	 */
	public final BloomFilter<E> getFilter(Collection<E> collection) {
		int f[] = new int[filterBytes/4];
		
		for(E e:collection) {
			add(f, e);
		}
		return new BloomFilter<>(this, f);
	}
	
	@Override
	public String toString() {
		return "BloomConfig [hashNum=" + hashNum + ", filterBytes=" + filterBytes + ", hashLength=" + hashLength
				+ ", hashFilter=" + hashFilter + "]";
	}

	void add(int[] f, E e) {
		hashConsumer.accept(e);
		
		for(int i = 0;i < hashNum;i++) {
			int start = (i * hashLength) / Byte.SIZE;
			int shift = (i * hashLength) % Byte.SIZE;
			int indexBit =
					((hashTmp[start+3] & 0xff) << Byte.SIZE*3) |
					((hashTmp[start+2] & 0xff) << Byte.SIZE*2) |
					((hashTmp[start+1] & 0xff) << Byte.SIZE*1) |
					 (hashTmp[start]   & 0xff);
			indexBit = (indexBit >> shift) & hashFilter;
			f[indexBit/Integer.SIZE] |= (1 << (indexBit%Integer.SIZE));
		}
	}
	
	boolean contains(int[] f, E e) {
		hashConsumer.accept(e);
		
		for(int i = 0;i < hashNum;i++) {
			int start = (i * hashLength) / Byte.SIZE;
			int shift = (i * hashLength) % Byte.SIZE;
			int indexBit =
					((hashTmp[start+3] & 0xff) << Byte.SIZE*3) |
					((hashTmp[start+2] & 0xff) << Byte.SIZE*2) |
					((hashTmp[start+1] & 0xff) << Byte.SIZE*1) |
					 (hashTmp[start]   & 0xff);
			indexBit = (indexBit >> shift) & hashFilter;
			
			if((f[indexBit/Integer.SIZE] & (1 << (indexBit%Integer.SIZE))) == 0) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * SHA-256をハッシュ関数としたBloomConfigを作成する．
	 * 
	 * @param hashNum 利用するハッシュ関数の数
	 * @param filterBytes BloomFilterのbyte長
	 * @param converter 要素をSHA-256の入力byte列に変換する関数
	 * @param salt SHA-256に用いるソルト値
	 * @return
	 */
	
	public static <T> BloomConfig<T> withSHA256(int hashNum, int filterBytes, Function<T, Byte[]> converter, byte[] salt) {
		
		int hashLength = 32 - Integer.numberOfLeadingZeros(filterBytes*8 - 1);
		if(hashLength * hashNum > 256) {
			throw new IllegalArgumentException("Too much hashNum or filterBytes!");
		}
		
		try {
			byte[] buff = new byte[32+3];
			ByteBuffer bb = ByteBuffer.wrap(buff);
			
			Consumer<T> hashConsumer = new Sha256Consumer<>(bb, salt, converter);
			
			return new BloomConfig<>(hashNum, filterBytes, 32, hashConsumer, buff);
		} catch (NoSuchAlgorithmException e1) {
			throw new RuntimeException(e1);
		}
	}
	
	private static class Sha256Consumer<T> implements Consumer<T> {
		
		final MessageDigest sha256;
		final ByteBuffer bb;
		final byte[] salt;
		
		final Function<T, Byte[]> converter;
		
		final byte[] tmp = new byte[32];
		
		public Sha256Consumer(ByteBuffer bb, byte[] salt, Function<T, Byte[]> converter) throws NoSuchAlgorithmException {
			sha256 = MessageDigest.getInstance("SHA-256");
			this.bb = bb;
			this.salt = salt;
			this.converter = converter;
		}
		
		@Override
		public void accept(T t) {
			sha256.update(salt);
			Byte[] b = converter.apply(t);
			for(int i = 0;i < b.length;i++) {
				tmp[i] = b[i];
			}
			bb.position(0);
			bb.put(sha256.digest(tmp));
		}
	}
}
