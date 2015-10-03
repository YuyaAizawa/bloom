package com.lethe_river.bloom;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * 多byte長で表現されるBloom filter.
 * 
 * <p>BloomFilterの包含判定は擬陽性を伴うが，省スペース且つ高速である．
 * また，複数の集合のBloomFilterから和集合のBloomFilterを計算できる．
 * これらの処理を行うためには,同一の{@link BloomConfig}から生成したBloomFilterである必要がある．
 * 
 * @author YuyaAizawa
 *
 * @param <E> 要素の型
 */
public class BloomFilter<E> implements Cloneable {
	private final BloomConfig<E> config;
	private final int[] filter;
	
	BloomFilter(BloomConfig<E> config, int[] filter) {
		this.config = config;
		this.filter = filter;
	}
	
	/**
	 * フィルタに要素を追加する．
	 * @param element 追加する要素
	 */
	public void add(E element) {
		config.add(filter, element);
	}
	
	/**
	 * フィルタからすべての要素を削除する.
	 */
	public void clear() {
		for(int i = 0;i < filter.length;i++) {
			filter[i] = 0;
		}
	}
	
	/**
	 * フィルタがelementを含む可能性があるときtrueを返す．
	 * @param element　要素
	 * @return　elementを含む可能性があるときtrue
	 */
	public boolean contains(E element) {
		return config.contains(filter, element);
	}
	
	/**
	 * フィルタがtargetを包含するか判定する．
	 * 対象は同一の{@link BloomConfig}から生成される必要がある．
	 * @param target　包含されるBloomFilter
	 * @return　包含されるならtrue.ただし擬陽性の可能性がある
	 */
	public boolean contains(BloomFilter<E> target) {
		checkConfig(target);
		
		int[] a = this.filter;
		int[] b = target.filter;
		
		for(int i = 0;i < filter.length;i++) {
			if((a[i] & b[i]) != b[i]) {
					return false;
			}
		}
		return true;
	}
	
	/**
	 * このBloomFilterと対象の和を計算する．
	 * 対象は同一の{@link BloomConfig}から生成される必要がある．
	 * @param target　対象のBloomFilter
	 * @return 要素の和に対応するBloomFilter
	 */
	public BloomFilter<E> union(BloomFilter<E> target) {
		checkConfig(target);
		
		int[] a = this.filter;
		int[] b = target.filter;
		int[] c = new int[filter.length];
		
		for(int i = 0;i < filter.length;i++) {
			c[i] = a[i] | b[i];
		}
		return new BloomFilter<>(config, c);
	}
	
	private void checkConfig(BloomFilter<E> target) {
		if(!config.equals(target.config)) {
			throw new IllegalArgumentException("both filters must be genarated by the same generator");
		}
	}
	
	/**
	 * このBloomFilterのbyte配列表現を返す．
	 * @return byte配列表現
	 */
	public byte[] toByteArray() {
		ByteBuffer byteBuffer = ByteBuffer.allocate(filter.length * 4);
		IntBuffer intBuffer = byteBuffer.asIntBuffer();
		intBuffer.put(filter);
		
		return byteBuffer.array();
	}
	
	/**
	 * このBloomFilterのbyte配列表現を{@link java.io.InputStream}で取得する．
	 * @return byte配列表現のInputStream
	 */
	public InputStream toByteStream() {
		return new InputStream() {
			int index = 0;
			byte[] cach = new byte[4]; 
			ByteBuffer buffer = ByteBuffer.wrap(cach);
			
			@Override
			public int read() throws IOException {
				if(index == filter.length * 4) {
					return -1;
				}
				if(index % 4 == 0) {
					buffer.putInt(0, index / 4);
				}
				int retVal = cach[index % 4] & 0xff;
				index++;
				return retVal;
			}
		};
	}
	
	/**
	 * このBloomFilterを表す二進数の文字列を返す．
	 * @return　二進数の文字列
	 */
	public String toBinaryString() {
		StringBuilder sb = new StringBuilder();
		for(int i = filter.length-1;i >= 0;i--) {
			sb.append(toBinaryString(filter[i]));
		}
		return sb.toString();
	}
	
	private static String toBinaryString(int i) {
		String notFilled = Integer.toBinaryString(i);
		StringBuilder sb = new StringBuilder();
		for(int j = 32 - notFilled.length();j > 0;j--) {
			sb.append("0");
		}
		return sb.toString()+notFilled;
	}
	
	@Override
	protected Object clone() throws CloneNotSupportedException {
		return new BloomFilter<>(config, Arrays.copyOf(filter, filter.length));
	}
	
	@Override
	public int hashCode() {
		return config.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		BloomFilter<?> other = (BloomFilter<?>) obj;
		
		if (!config.equals(other.config))
			return false;
		if (!Arrays.equals(filter, other.filter))
			return false;
		return true;
	}

	/*
	 * さんぷるだよ．消すかもよ．
	 */
	public static void main(String[] args) {
		
		BloomConfig<String> bloomConfig = BloomConfig.getInstance(String::hashCode, 2, 16);
		
		String str = new String("abc");
		
		System.out.println("-- hashCode ver. --");
		BloomFilter<String> abcDef = bloomConfig.getFilter(Arrays.asList("abc", "def"));
		BloomFilter<String> abc    = bloomConfig.getFilter("abc");
		
		System.out.println("[abc]     :"+bloomConfig.getFilter("abc").toBinaryString());
		System.out.println("[def]     :"+bloomConfig.getFilter("def").toBinaryString());
		System.out.println("[ghi]     :"+bloomConfig.getFilter("ghi").toBinaryString());
		System.out.println("[jkl]     :"+bloomConfig.getFilter("jkl").toBinaryString());
		System.out.println("[abc, def]:"+abcDef.toBinaryString());
		System.out.println();
		System.out.println("[abc, def]"+(abcDef.contains(bloomConfig.getFilter(str)) ? " may contain" : " does not contain") + " [abc]");
		System.out.println("[abc]"+(abc.contains(bloomConfig.getFilter("def")) ? " may contain" : " does not contain") + " [def]");
		
		
		System.out.println();
		System.out.println();
		System.out.println("-- sha256 ver. --");
		BloomConfig<String> bloomConfig2 = BloomConfig.withSHA256(4, 16, s -> {
			byte[] b = s.getBytes();
			Byte[] c = new Byte[b.length];
			for(int i = 0;i < b.length;i++) {
				c[i] = b[i];
			}
			return c;
		}, new byte[]{1,2,3});
		BloomFilter<String> abcDef2 = bloomConfig2.getFilter(Arrays.asList("abc", "def"));
		BloomFilter<String> abc2    = bloomConfig2.getFilter("abc");
		
		System.out.println("[abc]     :"+bloomConfig2.getFilter("abc").toBinaryString());
		System.out.println("[def]     :"+bloomConfig2.getFilter("def").toBinaryString());
		System.out.println("[ghi]     :"+bloomConfig2.getFilter("ghi").toBinaryString());
		System.out.println("[jkl]     :"+bloomConfig2.getFilter("jkl").toBinaryString());
		System.out.println("[abc, def]:"+abcDef2.toBinaryString());
		System.out.println();
		System.out.println("[abc, def]"+(abcDef2.contains(bloomConfig2.getFilter(str)) ? " may contain" : " does not contain") + " [abc]");
		System.out.println("[abc]"+(abc2.contains(bloomConfig2.getFilter("def")) ? " may contain" : " does not contain") + " [def]");
		System.out.println("[abc]"+(abc2.contains(bloomConfig.getFilter("def")) ? " may contain" : " does not contain") + " [def]");
	}
}
