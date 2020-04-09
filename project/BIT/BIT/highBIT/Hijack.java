package BIT.highBIT;

import BIT.lowBIT.*;

public class Hijack {

    public static String StringArray = "([Ljava/lang/String;)V";

    public static void arg(String classname, String methodname, Routine routine, String argtype){

        Instruction instr = routine.instructions.firstElement();

        Instruction target = (Instruction)routine.
                modified_instructions.elementAt(instr.modified_index);

        target.setIndex(instr.modified_index);

        short classUtf8Index = setConstantPoolEntry(classname,routine);

        CONSTANT_Class_Info classInfo = new
                CONSTANT_Class_Info(classUtf8Index);

        short classInfoIndex = addwithcheck(routine, classInfo);

        short methodUtf8Index = setConstantPoolEntry(methodname,routine);

        short descriptorUtf8Index = setConstantPoolEntry(argtype,routine);

        CONSTANT_NameAndType_Info nameAndTypeInfo = new
                CONSTANT_NameAndType_Info(
                        methodUtf8Index,
                        descriptorUtf8Index);

        short nameAndTypeInfoIndex = addwithcheck(routine, nameAndTypeInfo);

        CONSTANT_Methodref_Info methodRefInfo = new
                CONSTANT_Methodref_Info(classInfoIndex, nameAndTypeInfoIndex);

        short methodRefInfoIndex = addwithcheck(routine, methodRefInfo);

        InstructionDoubleOperand invokestatic = new InstructionDoubleOperand(
                InstructionTable.invokestatic,
                (short)methodRefInfoIndex,
                routine
        );

        Instruction nop = new Instruction(InstructionTable.NOP_INSTRUCTION, routine);

        Instruction larg = new Instruction(InstructionTable.aload_1, routine);

        Instruction dummy = (Instruction)routine.modified_instructions.elementAt(target.index);

        routine.modified_instructions.insertElementAt(invokestatic, target.index);
        routine.modified_instructions.insertElementAt(larg, target.index);

        //for exceptions table and other things
        routine.modified_instructions.insertElementAt(nop, target.index);
        routine.modified_instructions.insertElementAt(nop, target.index);
        routine.modified_instructions.insertElementAt(nop, target.index);
        routine.modified_instructions.insertElementAt(nop, target.index);

        int increment = 6;
        routine.adjInstrOffsets(dummy.getOffset(), 8, true);
        routine.adjModifiedBasicBlocks(target.index, increment, true);
        routine.adjOffsets(dummy.getOffset(), 8, true);

        routine.max_stack++;

        routine.instructions.updateModifiedIndex(instr.index, increment);
    }

    private static short setConstantPoolEntry(String name,Routine routine){
        CONSTANT_Utf8_Info nameUtf8 = new CONSTANT_Utf8_Info(name);

        return addwithcheck(routine, nameUtf8);
    }

    private static short addwithcheck( Routine routine,Cp_Info nameUtf8){

        short nameUtf8Index = 0;

        if ((nameUtf8Index = routine.indexInConstantPool(nameUtf8)) == -1)
            nameUtf8Index = routine.addConstantPoolEntry(nameUtf8);

        return nameUtf8Index;
    }
}
