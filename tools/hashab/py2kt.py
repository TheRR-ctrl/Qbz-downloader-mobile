#!/usr/bin/env python3
"""Turns the generated Python of ipodsync's hashAB port into Kotlin.

Used once to produce app/src/main/java/com/qbdlx/mobile/ipod/HashAbGen*.kt; kept for provenance, not part of the build.

    python3 py2kt.py <ipodsync/src/ipodsync dir> <output dir>

hashAB is a public-domain algorithm (https://github.com/DigiDNA/hashab, reverse engineered from libhashab).
ipodsync (MIT, Copyright (c) 2026 Alex Buldiei) ported it to Python; this file reads that Python's syntax tree
and writes the same arithmetic as Kotlin. Every value is a 32-bit Int: + - * & | ^ << behave the same in C, Python
(masked to 32 bits) and Kotlin, so the only things translated with care are operator precedence (every operation
is parenthesised, because Kotlin's infix `and`/`or`/`xor` don't rank like C's &, |, ^), shifts (always logical, and
a count of 32 or more gives 0 like Python's masked shift) and division (unsigned).
"""
import ast
import sys
from pathlib import Path

KOTLIN_WORDS = {"in", "is", "as", "fun", "val", "var", "when", "object", "class", "interface", "typeof", "null", "true", "false",
                "this", "super", "return", "break", "continue", "do", "else", "for", "if", "while", "package", "try", "throw",
                "typealias", "it", "out", "by", "init", "get", "set"}


def ident(name: str) -> str:
    return name + "_" if name in KOTLIN_WORDS else name


def s32(v: int) -> int:
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v >= 0x80000000 else v


def lit(v: int) -> str:
    v = s32(v)
    if v == -2147483648:
        return "Int.MIN_VALUE"
    return f"({v})" if v < 0 else str(v)


BYTE_CALLS = {
    "u8c": "(({0}) and 0xFF)", "LOBYTE": "(({0}) and 0xFF)", "u16c": "(({0}) and 0xFFFF)", "LOWORD": "(({0}) and 0xFFFF)",
    "i8c": "(({0}).toByte().toInt())", "i16c": "(({0}).toShort().toInt())",
    "BYTE1": "((({0}) ushr 8) and 0xFF)", "BYTE2": "((({0}) ushr 16) and 0xFF)",
    "HIBYTE": "((({0}) ushr 24) and 0xFF)", "HIWORD": "((({0}) ushr 16) and 0xFFFF)",
}
FIELD = {"masked_and": "maskedAnd", "masked_or": "maskedOr"}


class Conv:
    def __init__(self, arrays):
        self.arrays = arrays

    def expr(self, n) -> str:
        if isinstance(n, ast.Constant):
            if isinstance(n.value, bool):
                return "true" if n.value else "false"
            return lit(n.value)
        if isinstance(n, ast.Name):
            return ident(n.id)
        if isinstance(n, ast.BinOp):
            if isinstance(n.op, ast.Mult) and isinstance(n.left, ast.List) and len(n.left.elts) == 1:
                e = self.expr(n.left.elts[0])
                return f"IntArray({self.expr(n.right)})" if e == "0" else f"IntArray({self.expr(n.right)}) {{ {e} }}"
            a, b = self.expr(n.left), self.expr(n.right)
            op = n.op
            if isinstance(op, ast.Add): return f"({a} + {b})"
            if isinstance(op, ast.Sub): return f"({a} - {b})"
            if isinstance(op, ast.Mult): return f"({a} * {b})"
            if isinstance(op, ast.BitAnd): return f"({a} and {b})"
            if isinstance(op, ast.BitOr): return f"({a} or {b})"
            if isinstance(op, ast.BitXor): return f"({a} xor {b})"
            if isinstance(op, ast.LShift): return f"shl({a}, {b})"
            if isinstance(op, ast.RShift): return f"shr({a}, {b})"
            if isinstance(op, (ast.Div, ast.FloorDiv)): return f"Integer.divideUnsigned({a}, {b})"
            if isinstance(op, ast.Mod): return f"Integer.remainderUnsigned({a}, {b})"
            raise ValueError(f"operator {op}")
        if isinstance(n, ast.UnaryOp):
            v = self.expr(n.operand)
            if isinstance(n.op, ast.USub): return f"(-{v})"
            if isinstance(n.op, ast.Invert): return f"({v}.inv())"
            raise ValueError(f"unary {n.op}")
        if isinstance(n, ast.Compare):
            ops = {ast.Eq: "==", ast.NotEq: "!=", ast.Lt: "<"}
            return f"({self.expr(n.left)} {ops[type(n.ops[0])]} {self.expr(n.comparators[0])})"
        if isinstance(n, ast.IfExp):
            return f"(if ({self.expr(n.test)}) {self.expr(n.body)} else {self.expr(n.orelse)})"
        if isinstance(n, ast.Subscript):
            if isinstance(n.slice, ast.Slice):
                lo = self.expr(n.slice.lower) if n.slice.lower else "0"
                return f"{self.expr(n.value)}.copyOfRange({lo}, {self.expr(n.slice.upper)})"
            return f"{self.expr(n.value)}[{self.expr(n.slice)}]"
        if isinstance(n, ast.Attribute):
            return f"{self.expr(n.value)}.{FIELD.get(n.attr, n.attr)}"
        if isinstance(n, ast.List):
            return "intArrayOf(" + ", ".join(self.expr(e) for e in n.elts) + ")"
        if isinstance(n, ast.Call):
            f = n.func.id if isinstance(n.func, ast.Name) else None
            if isinstance(n.func, ast.Attribute) and n.func.attr == "from_bytes":  # int.from_bytes(b'..', 'little')
                return lit(int.from_bytes(n.args[0].value, "little"))
            args = [self.expr(a) for a in n.args]
            if f == "U32" or f == "int":
                return args[0]
            if f in BYTE_CALLS:
                return BYTE_CALLS[f].format(args[0])
            if f == "bytearray":
                return f"IntArray({args[0]})"
            if f == "bytes" or f == "list":
                return f"{args[0]}.copyOf()"
            if f == "_gbsm":
                return f"generateBufferFromStateMixing({', '.join(args)})"
            if f == "_u32_list":
                return f"u32List({', '.join(args)})"
            if f is None:
                raise ValueError(f"call {ast.unparse(n.func)}")
            return f"{ident(f)}({', '.join(args)})"
        raise ValueError(f"expression {type(n).__name__}: {ast.unparse(n)[:80]}")

    # ---- statements --------------------------------------------------------------------------------------------
    def is_array_value(self, n) -> bool:
        if isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id in ("bytearray", "list", "bytes", "_u32_list"):
            return True
        if isinstance(n, ast.BinOp):
            if isinstance(n.op, ast.Mult) and isinstance(n.left, ast.List):
                return True
            if isinstance(n.op, ast.Add):
                return self.is_array_value(n.left) or self.is_array_value(n.right)
        if isinstance(n, ast.List):
            return True
        return False

    def stmts(self, body, ind, out):
        pad = "    " * ind
        for s in body:
            if isinstance(s, ast.Expr) and isinstance(s.value, ast.Constant):
                continue
            if isinstance(s, ast.Expr) and isinstance(s.value, ast.Call):  # a call used as a statement
                out.append(f"{pad}{self.expr(s.value)}")
            elif isinstance(s, ast.Assign):
                value = self.expr(s.value)
                if len(s.targets) == 1:
                    out.append(f"{pad}{self.expr(s.targets[0])} = {value}")
                else:  # a = b = value: evaluate once
                    out.append(f"{pad}run {{ val chained = {value}")
                    for t in s.targets:
                        out.append(f"{pad}    {self.expr(t)} = chained")
                    out.append(f"{pad}}}")
            elif isinstance(s, ast.AugAssign):
                op = {ast.Add: "+="}[type(s.op)]
                out.append(f"{pad}{self.expr(s.target)} {op} {self.expr(s.value)}")
            elif isinstance(s, ast.For):
                r = s.iter.args
                assert isinstance(s.iter, ast.Call) and s.iter.func.id == "range"
                v = ident(s.target.id)
                if len(r) == 1:
                    head = f"for ({v} in 0 until {self.expr(r[0])})"
                elif len(r) == 2:
                    head = f"for ({v} in {self.expr(r[0])} until {self.expr(r[1])})"
                else:
                    head = f"for ({v} in {self.expr(r[0])} until {self.expr(r[1])} step {self.expr(r[2])})"
                out.append(f"{pad}{head} {{")
                self.stmts(s.body, ind + 1, out)
                out.append(f"{pad}}}")
            elif isinstance(s, ast.While):
                out.append(f"{pad}while ({self.expr(s.test)}) {{")
                self.stmts(s.body, ind + 1, out)
                out.append(f"{pad}}}")
            elif isinstance(s, ast.If):
                out.append(f"{pad}if ({self.expr(s.test)}) {{")
                self.stmts(s.body, ind + 1, out)
                if s.orelse:
                    out.append(f"{pad}}} else {{")
                    self.stmts(s.orelse, ind + 1, out)
                out.append(f"{pad}}}")
            elif isinstance(s, ast.Break):
                out.append(f"{pad}break")
            elif isinstance(s, ast.Return):
                out.append(f"{pad}return {self.expr(s.value)}")
            else:
                raise ValueError(f"statement {type(s).__name__}: {ast.unparse(s)[:80]}")


def assigned_names(body):
    names, loop_vars = [], set()
    for n in ast.walk(ast.Module(body=body, type_ignores=[])):
        if isinstance(n, ast.For):
            loop_vars.add(n.target.id)
        if isinstance(n, (ast.Assign, ast.AugAssign)):
            for t in (n.targets if isinstance(n, ast.Assign) else [n.target]):
                if isinstance(t, ast.Name) and t.id not in names:
                    names.append(t.id)
    return names, loop_vars


def convert_gen(path: Path, out_dir: Path):
    """generate_buffer_from_state_mixing: 1,500 straight-line statements, split over many small methods."""
    fn = next(n for n in ast.parse(path.read_text()).body if isinstance(n, ast.FunctionDef))
    body = [s for s in fn.body if not (isinstance(s, ast.Expr) and isinstance(s.value, ast.Constant))]
    assert isinstance(body[0], ast.Assign) and isinstance(body[0].value, ast.ListComp)  # seed = [make_seed(..)]
    body = body[1:]
    assert isinstance(body[-1], ast.Return)
    body = body[:-1]
    names, _ = assigned_names(body)
    names = [n for n in names if n != "seed"]
    conv = Conv(set())
    chunks = [body[i:i + 45] for i in range(0, len(body), 45)]
    lines = [HEADER, "package com.qbdlx.mobile.ipod", "",
             "/** generate_buffer_from_state_mixing: mixes the 16-word seed into the 16-word state, in place. */",
             "internal fun generateBufferFromStateMixing(param_1: IntArray, param_2: IntArray) = MixBuf(param_1, param_2).run()", "",
             "private class MixBuf(private val param_1: IntArray, private val param_2: IntArray) {",
             "    private val seed = Array(16) { makeSeed(param_1[it]) }"]
    lines += [f"    private var {ident(n)} = 0" for n in names]
    lines += ["", "    fun run() {"] + [f"        c{i}()" for i in range(len(chunks))] + ["    }", ""]
    for i, ch in enumerate(chunks):
        lines.append(f"    private fun c{i}() {{")
        conv.stmts(ch, 2, lines)
        lines += ["    }", ""]
    lines.append("}")
    (out_dir / "HashAbGen.kt").write_text("\n".join(lines) + "\n")
    return len(body), len(chunks)


SIGNATURES = {
    "read_state_table": ("offset: Int", "Int"), "read_sbox": ("idx: Int", "Int"), "idx64": ("x: Int", "Int"),
    "xform_v318": ("v: Int", "Int"), "mix_byte": ("x: Int", "Int"), "read316": ("v: Int", "Int"), "read317": ("v: Int", "Int"),
    "v318_mask": ("v: Int", "Int"), "read318": ("v: Int", "Int"), "read318_off": ("v: Int", "Int"),
    "process_input_chunk": ("byte0: Int, byte1: Int, byte2: Int, byte3: Int", "Int"),
    "generate_key_material": ("a1: IntArray", "IntArray"), "mix_with_v318": ("a1: IntArray, v318: IntArray", "Unit"),
    "mix_with_v317": ("a1: IntArray, v317: IntArray", "Unit"), "fold_v315_into_output": ("a1: IntArray, v315: IntArray", "Unit"),
    "generate_initial_buffer": ("a2: IntArray", "IntArray"),
}
HAND_WRITTEN = {"_t", "_u32_list", "read_u32"}


def convert_gen2(path: Path, out_dir: Path):
    tree = ast.parse(path.read_text())
    lines = [HEADER, "package com.qbdlx.mobile.ipod", ""]
    init_const = next(n for n in tree.body if isinstance(n, ast.Assign) and getattr(n.targets[0], "id", "") == "INIT_CONST")
    values = [s32(e.operand.value * -1 if isinstance(e, ast.UnaryOp) else e.value) for e in init_const.value.generators[0].iter.elts]
    lines.append("private val INIT_CONST = intArrayOf(" + ", ".join(lit(v) for v in values) + ")")
    for t in ("GEN190_SBOX", "GEN190_STATE_TABLE", "IBUFF_KEY_TABLE", "IBUFF_MIX_SEED_A", "IBUFF_MIX_SEED_B"):
        lines.append(f'private val {t} = HashAbTables.get("{t}")')
    lines.append("")
    lines.append("private fun u32List(blob: IntArray, n: Int) = IntArray(n) { i ->")
    lines.append("    blob[4 * i] or (blob[4 * i + 1] shl 8) or (blob[4 * i + 2] shl 16) or (blob[4 * i + 3] shl 24)")
    lines.append("}")
    lines.append("")
    lines.append("private fun read_u32(tab: IntArray, offset: Int): Int =")
    lines.append("    tab[offset] or (tab[offset + 1] shl 8) or (tab[offset + 2] shl 16) or (tab[offset + 3] shl 24)")
    lines.append("")
    for fn in [n for n in tree.body if isinstance(n, ast.FunctionDef)]:
        if fn.name in HAND_WRITTEN:
            continue
        params, ret = SIGNATURES[fn.name]
        body = [s for s in fn.body if not (isinstance(s, ast.Expr) and isinstance(s.value, ast.Constant))]
        names, loop_vars = assigned_names(body)
        arrays = set()
        probe = Conv(arrays)
        for _ in range(3):  # a few passes: arrays can be built from other arrays
            for n in ast.walk(ast.Module(body=body, type_ignores=[])):
                if isinstance(n, ast.Assign) and probe.is_array_value(n.value):
                    for t in n.targets:
                        if isinstance(t, ast.Name):
                            arrays.add(t.id)
        conv = Conv(arrays)
        pnames = {p.split(":")[0] for p in params.split(", ")}
        lines.append(f"private fun {ident(fn.name)}({params}): {ret} {{")
        for nme in names:
            if nme in pnames or (nme in loop_vars and nme not in arrays):
                continue
            lines.append(f"    var {ident(nme)} = " + ("IntArray(0)" if nme in arrays else "0"))
        conv.stmts(body, 1, lines)
        lines += ["}", ""]
    # the two entry points keep their names
    (out_dir / "HashAbGen2.kt").write_text("\n".join(lines).replace("private fun generate_key_material", "internal fun generate_key_material")
                                          .replace("private fun generate_initial_buffer", "internal fun generate_initial_buffer") + "\n")


HEADER = """// GENERATED by tools/hashab/py2kt.py - do not edit by hand.
//
// hashAB is a public-domain algorithm (https://github.com/DigiDNA/hashab). This file is the arithmetic of ipodsync's
// pure-Python port of it (MIT, Copyright (c) 2026 Alex Buldiei, https://github.com/buldiei/ipodsync) rewritten as
// Kotlin by reading that port's syntax tree. See THIRD_PARTY.md."""

if __name__ == "__main__":
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    print("HashAbGen.kt:", convert_gen(src / "_hashab_gen.py", out))
    convert_gen2(src / "_hashab_gen2.py", out)
    print("HashAbGen2.kt written")
