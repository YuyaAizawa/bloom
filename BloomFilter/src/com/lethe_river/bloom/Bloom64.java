package com.lethe_river.bloom;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Function;

/**
 * long値で表現されるBloom filterを生成するクラス.
 * 
 * @see Bloom32
 * @see BloomConfig
 * @author YuyaAizawa
 *
 * @param <E> 要素の型
 */
public final class Bloom64<E> {
	// 64bitのフィルターにフラグを立てるにはhashは6bitで十分
	private static final int HASH_LENGTH = 6;
	private static final int HASH_FILTER = (1 << HASH_LENGTH)-1;
	
	private final Function<E, Integer> hashFunction;
	private final int k;
	
	/**
	 * 利用するハッシュ関数の数と元となるハッシュ関数を指定してBloom filterの生成器をつくる.
	 * int値を返すハッシュ関数からは最大5つのハッシュ値を利用できる.
	 * 元となるハッシュ関数から得たint値のハッシュは，内部で6bit毎に分割されて利用される.
	 * 
	 * @param k 利用するハッシュ関数の数(1~5)
	 * @param hashFunction 元となるハッシュ関数
	 */
	public Bloom64(int k, Function<E, Integer> hashFunction) {
		if(!(1 <= k && k <= Integer.SIZE/HASH_LENGTH)) {
			// 5つもできれば十分ですよね...
			throw new IllegalArgumentException();
		}
		this.k = k;
		this.hashFunction = hashFunction;
	}
	
	/**
	 * 利用するハッシュ関数の数を指定してBloom filterの生成器を作る.
	 * 元となるハッシュ関数としてhashCode()を利用する.
	 * 
	 * @param k 利用するハッシュ関数の数(1~5)
	 */
	public Bloom64(int k) {
		this(k, null);
	}
	
	/**
	 * Bloom filterを返す.
	 * @param object フィルターするオブジェクト
	 * @return objectから計算されたBloom filter
	 */
	public long getFilter(E object) {
		long ret = 0;
		int hash = hashFunction == null ? object.hashCode() : hashFunction.apply(object);
		for(int i = 0; i < k;i++) {
			ret |= 1L << (hash & HASH_FILTER);
			hash >>= HASH_LENGTH;
		}
		return ret;
	}
	
	/**
	 * Bloom filterを返す.
	 * @param collection フィルターするオブジェクトを含むコレクション
	 * @return collectionから計算されたBloom filter
	 */
	public final Long getFilter(Collection<E> collection) {
		return collection.stream()
				.map(t -> getFilter(t))
				.reduce(0L, (l,r) -> l | r);
	}
	
	/**
	 * Bloom filter間の包含関係を判定する.
	 * @param sup 包含するBloom filter
	 * @param sub 包含されるBloom filter
	 * @return 包含すればtrue
	 */
	public static boolean isContained(long sup, long sub) {
		return ((sup & sub) ^ sub) == 0L;
	}
	
	public static void main(String[] args) {
		Bloom64<String> bloom = new Bloom64<>(2);
		
		String str = new String("abc");
		
		long filterAbcDef = bloom.getFilter(Arrays.asList("abc", "def"));
		long filterAbc    = bloom.getFilter("abc");
		
		System.out.println("abc:"+toBinaryStringZeroFill(bloom.getFilter("abc")));
		System.out.println("def:"+toBinaryStringZeroFill(bloom.getFilter("def")));
		System.out.println("ghi:"+toBinaryStringZeroFill(bloom.getFilter("ghi")));
		System.out.println();
		System.out.println("[abc, def]"+(Bloom64.isContained(filterAbcDef, bloom.getFilter(str)) ? " may contain" : " does not contain") + " [abc]");
		System.out.println("[abc]"+(Bloom64.isContained(filterAbc,bloom.getFilter("def")) ? " may contain" : " does not contain") + " [def]");
	}
	
	private static String toBinaryStringZeroFill(long i) {
		String notFilled = Long.toBinaryString(i);
		StringBuilder sb = new StringBuilder();
		for(int j = 64 - notFilled.length();j > 0;j--) {
			sb.append("0");
		}
		return sb.toString()+notFilled;
	}
}
