/*
 * [The "BSD licence"]
 * Copyright (c) 2009 Ben Gruver
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.jf.dexlib.Code.Format;

import org.jf.dexlib.Code.Instruction;
import org.jf.dexlib.Code.Opcode;
import org.jf.dexlib.Util.NumberUtils;
import org.jf.dexlib.Util.Output;
import org.jf.dexlib.DexFile;

import java.util.Iterator;

public class SparseSwitchDataPseudoInstruction extends Instruction {
    public static final Instruction.InstructionFactory Factory = new Factory();

    @Override
    public int getSize() {
        return getTargetCount() * 8 + 4;
    }

    public static void emit(Output out, int[] keys, int[] targets) {
        if (keys.length != targets.length) {
            throw new RuntimeException("The number of keys and offsets don't match");
        }

        if (targets.length == 0) {
            throw new RuntimeException("The sparse-switch data must contain at least 1 key/target");
        }

        if (targets.length > 0xFFFF) {
            throw new RuntimeException("The sparse-switch data contains too many elements. " +
                    "The maximum number of switch elements is 65535");
        }

        //write out padding, if necessary
        if (out.getCursor() % 4 != 0) {
            out.writeShort(0);
        }

        out.writeByte(0x00);
        out.writeByte(0x02);
        out.writeShort(targets.length);

        if (targets.length > 0) {
            int key = keys[0];

            out.writeInt(key);

            for (int i = 1; i < keys.length; i++) {
                key = keys[i];
                if (key <= keys[i - 1]) {
                    throw new RuntimeException("The targets in a sparse switch block must be sorted in ascending" +
                            "order, by key");
                }
                out.writeInt(key);
            }

            for (int target : targets) {
                out.writeInt(target);
            }
        }
    }

    public SparseSwitchDataPseudoInstruction(byte[] buffer, int bufferIndex) {
        super(Opcode.NOP, buffer, bufferIndex);

        byte opcodeByte = buffer[bufferIndex++];
        if (opcodeByte != 0x00) {
            throw new RuntimeException("Invalid opcode byte for a SparseSwitchData pseudo-instruction");
        }
        byte subopcodeByte = buffer[bufferIndex];
        if (subopcodeByte != 0x02) {
            throw new RuntimeException("Invalid sub-opcode byte for a SparseSwitchData pseudo-instruction");
        }
    }

    public Format getFormat() {
        return Format.SparseSwitchData;
    }

    public int getTargetCount() {
        return NumberUtils.decodeUnsignedShort(buffer, bufferIndex + 2);
    }

    public static class SparseSwitchTarget {
        public int value;
        public int target;
    }

    public Iterator<SparseSwitchTarget> getTargets() {
        return new Iterator<SparseSwitchTarget>() {
            final int targetCount = getTargetCount();
            int i = 0;
            int valuePosition = bufferIndex + 4;
            int targetPosition = bufferIndex + 4 + targetCount * 4;

            SparseSwitchTarget sparseSwitchTarget = new SparseSwitchTarget();

            public boolean hasNext() {
                return i<targetCount;
            }

            public SparseSwitchTarget next() {
                sparseSwitchTarget.value = NumberUtils.decodeInt(buffer, valuePosition);
                sparseSwitchTarget.target = NumberUtils.decodeInt(buffer, targetPosition);
                valuePosition+=4;
                targetPosition+=4;
                i++;
                return sparseSwitchTarget;
            }

            public void remove() {
            }
        };
    }

    private static class Factory implements Instruction.InstructionFactory {
        public Instruction makeInstruction(DexFile dexFile, Opcode opcode, byte[] buffer, int bufferIndex) {
            if (opcode != Opcode.NOP) {
                throw new RuntimeException("The opcode for a SparseSwitchDataPseudoInstruction must by NOP");
            }
            return new SparseSwitchDataPseudoInstruction(buffer, bufferIndex);
        }
    }
    
}
