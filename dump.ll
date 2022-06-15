didopt def @"M28java.lang.ThreadLocal$ValuesD6rehashzEPT28java.lang.ThreadLocal$Values" : (@"T28java.lang.ThreadLocal$Values") => bool {
%20000(%1 : @"T28java.lang.ThreadLocal$Values"):
  %440003 = ine[@"T28java.lang.ThreadLocal$Values"] %1 : @"T28java.lang.ThreadLocal$Values", null
  if %440003 : bool then %440001 else %440002
%440001:
  %440004 = elem[{ptr, ptr, array[@"T16java.lang.Object"], int, int, int}] %1 : @"T28java.lang.ThreadLocal$Values", int 0, int 3
  %30001 = load[int] %440004 : ptr
  %440006 = ine[@"T28java.lang.ThreadLocal$Values"] %1 : @"T28java.lang.ThreadLocal$Values", null
  if %440006 : bool then %440005 else %440002
%440005:
  %440007 = elem[{ptr, ptr, array[@"T16java.lang.Object"], int, int, int}] %1 : @"T28java.lang.ThreadLocal$Values", int 0, int 5
  %40001 = load[int] %440007 : ptr
  %20004 = call[(@"T28java.lang.ThreadLocal$Values") => int] @"M28java.lang.ThreadLocal$ValuesD11maximumLoadiEPT28java.lang.ThreadLocal$Values" : ptr(%1 : @"T28java.lang.ThreadLocal$Values")
  %20006 = iadd[int] %30001 : int, %40001 : int
  %20007 = slt[int] %20006 : int, %20004 : int
  if %20007 : bool then %50000 else %60000
%50000:
  %440008 = call[() => unit] @"M2__C22scalanative_yieldpoint" : () => unit()
  ret false
%60000:
  jump %70000
%70000:
  %440010 = ine[@"T28java.lang.ThreadLocal$Values"] %1 : @"T28java.lang.ThreadLocal$Values", null
  if %440010 : bool then %440009 else %440002
%440009:
  %440011 = elem[{ptr, ptr, array[@"T16java.lang.Object"], int, int, int}] %1 : @"T28java.lang.ThreadLocal$Values", int 0, int 2
  %80001 = load[array[@"T16java.lang.Object"]] %440011 : ptr
  %440013 = ine[array[@"T16java.lang.Object"]] %80001 : array[@"T16java.lang.Object"], null
  if %440013 : bool then %440012 else %440002
%440012:
  %440014 = elem[{ptr, ptr, int, int, [nothing x 0]}] %80001 : array[@"T16java.lang.Object"], int 0, int 2
  %70001 = load[int] %440014 : ptr
  %440015 = and[int] int 1, int 31
  %70003 = ashr[int] %70001 : int, %440015 : int
  switch %70003 : int { default => %90000 }
%90000:
  %440017 = ine[@"T28java.lang.ThreadLocal$Values"] %1 : @"T28java.lang.ThreadLocal$Values", null
  if %440017 : bool then %440016 else %440002
%440016:
  %440018 = elem[{ptr, ptr, array[@"T16java.lang.Object"], int, int, int}] %1 : @"T28java.lang.ThreadLocal$Values", int 0, int 5
  %100001 = load[int] %440018 : ptr
  %90002 = sgt[int] %100001 : int, %70003 : int
  if %90002 : bool then %110000 else %120000
%110000:
  %440019 = and[int] int 1, int 31
  %110002 = shl[int] %70003 : int, %440019 : int
  jump %130000(%110002 : int)
%120000:
  jump %130000(%70003 : int)
%130000(%130001 : int):
  jump %140000
%140000:
  %440021 = ine[@"T28java.lang.ThreadLocal$Values"] %1 : @"T28java.lang.ThreadLocal$Values", null
  if %440021 : bool then %440020 else %440002
%440020:
  %440022 = elem[{ptr, ptr, array[@"T16java.lang.Object"], int, int, int}] %1 : @"T28java.lang.ThreadLocal$Values", int 0, int 2
  %150001 = load[array[@"T16java.lang.Object"]] %440022 : ptr
  %440023 = copy @"M38scala.scalanative.runtime.ObjectArray$G8instance" : ptr
  %140001 = call[(@"T38scala.scalanative.runtime.ObjectArray$", int) => @"T37scala.scalanative.runtime.ObjectArray"] @"M38scala.scalanative.runtime.ObjectArray$D5allociL37scala.scalanative.runtime.ObjectArrayEO" : ptr(%440023 : ptr, %130001 : int)
  %440026 = ine[@"T28java.lang.ThreadLocal$Values"] %1 : @"T28java.lang.ThreadLocal$Values", null
  if %440026 : bool then %440025 else %440002
%440025:
  %440027 = elem[{ptr, ptr, array[@"T16java.lang.Object"], int, int, int}] %1 : @"T28java.lang.ThreadLocal$Values", int 0, int 2
  %440024 = store[array[@"T16java.lang.Object"]] %440027 : ptr, %140001 : !?@"T37scala.scalanative.runtime.ObjectArray"
  %440030 = ine[@"T28java.lang.ThreadLocal$Values"] %1 : @"T28java.lang.ThreadLocal$Values", null
  if %440030 : bool then %440029 else %440002
%440029:
  %440031 = elem[{ptr, ptr, array[@"T16java.lang.Object"], int, int, int}] %1 : @"T28java.lang.ThreadLocal$Values", int 0, int 3
  %440028 = store[int] %440031 : ptr, int 0
  %440033 = ine[@"T28java.lang.ThreadLocal$Values"] %1 : @"T28java.lang.ThreadLocal$Values", null
  if %440033 : bool then %440032 else %440002
%440032:
  %440034 = elem[{ptr, ptr, array[@"T16java.lang.Object"], int, int, int}] %1 : @"T28java.lang.ThreadLocal$Values", int 0, int 5
  %180001 = load[int] %440034 : ptr
  %140003 = ieq[int] %180001 : int, int 0
  if %140003 : bool then %190000 else %200000
%190000:
  %440035 = call[() => unit] @"M2__C22scalanative_yieldpoint" : () => unit()
  ret true
%200000:
  jump %210000
%210000:
  %440037 = ine[array[@"T16java.lang.Object"]] %150001 : array[@"T16java.lang.Object"], null
  if %440037 : bool then %440036 else %440002
%440036:
  %440038 = elem[{ptr, ptr, int, int, [nothing x 0]}] %150001 : array[@"T16java.lang.Object"], int 0, int 2
  %210001 = load[int] %440038 : ptr
  %210003 = isub[int] %210001 : int, int 2
  jump %220000(false, %210003 : int, %210003 : int)
%220000(%220001 : bool, %220002 : int, %220003 : int):
  %220005 = sge[int] %220002 : int, int 0
  if %220005 : bool then %230000 else %240000
%230000:
  %440041 = ine[array[@"T16java.lang.Object"]] %150001 : array[@"T16java.lang.Object"], null
  if %440041 : bool then %440040 else %440002
%440040:
  %440042 = elem[{ptr, ptr, int, int, [nothing x 0]}] %150001 : array[@"T16java.lang.Object"], int 0, int 2
  %440039 = load[int] %440042 : ptr
  %440045 = sge[int] %220002 : int, int 0
  %440046 = slt[int] %220002 : int, %440039 : int
  %440047 = and[bool] %440045 : bool, %440046 : bool
  if %440047 : bool then %440043 else %440044(%220002 : int)
%440043:
  %440048 = elem[{ptr, ptr, int, int, [@"T16java.lang.Object" x 0]}] %150001 : array[@"T16java.lang.Object"], int 0, int 4, %220002 : int
  %230001 = load[@"T16java.lang.Object"] %440048 : ptr
  %230003 = ieq[@"T16java.lang.Object"] %230001 : @"T16java.lang.Object", null
  if %230003 : bool then %250000 else %260000
%250000:
  jump %270000(true)
%260000:
  if %230003 : bool then %280000 else %290000
%280000:
  %280001 = call[() => @"T39java.lang.ThreadLocal$Values$Tombstone$"] @"M39java.lang.ThreadLocal$Values$Tombstone$G4load" : ptr()
  jump %300000(false)
%290000:
  %290001 = call[() => @"T39java.lang.ThreadLocal$Values$Tombstone$"] @"M39java.lang.ThreadLocal$Values$Tombstone$G4load" : ptr()
  %440050 = ine[@"T16java.lang.Object"] %230001 : @"T16java.lang.Object", null
  if %440050 : bool then %440049 else %440002
%440049:
  %440051 = load[ptr] %230001 : @"T16java.lang.Object"
  %440052 = elem[{{ptr, ptr, int, int, ptr}, int, int, {ptr}, [ptr x 4]}] %440051 : ptr, int 0, int 4, int 0
  %290003 = load[ptr] %440052 : ptr
  %290004 = call[(@"T16java.lang.Object", @"T16java.lang.Object") => bool] %290003 : ptr(%230001 : @"T16java.lang.Object", %290001 : !?@"T39java.lang.ThreadLocal$Values$Tombstone$")
  jump %300000(%290004 : bool)
%300000(%300001 : bool):
  jump %270000(%300001 : bool)
%270000(%270001 : bool):
  if %270001 : bool then %310000 else %320000
%310000:
  jump %330000(true)
%320000:
  jump %330000(false)
%330000(%330001 : bool):
  %330003 = xor[bool] %330001 : bool, true
  if %330003 : bool then %340000 else %350000
%340000:
  %440056 = ieq[@"T16java.lang.Object"] %230001 : @"T16java.lang.Object", null
  if %440056 : bool then %440054 else %440053
%440053:
  %440057 = load[ptr] %230001 : @"T16java.lang.Object"
  %440058 = elem[{ptr, ptr, int, int, ptr}] %440057 : ptr, int 0, int 2
  %440059 = load[int] %440058 : ptr
  %440060 = sle[int] int 244, %440059 : int
  %440061 = sle[int] %440059 : int, int 245
  %440062 = and[bool] %440060 : bool, %440061 : bool
  if %440062 : bool then %440054 else %440055(%230001 : @"T16java.lang.Object", @"M23java.lang.ref.ReferenceG4type" : ptr)
%440054:
  %340001 = bitcast[@"T23java.lang.ref.Reference"] %230001 : @"T16java.lang.Object"
  %340002 = call[(@"T23java.lang.ref.Reference") => @"T16java.lang.Object"] @"M27java.lang.ref.WeakReferenceD3getL16java.lang.ObjectEO" : ptr(%340001 : @"T23java.lang.ref.Reference")
  %440065 = ieq[@"T16java.lang.Object"] %340002 : @"T16java.lang.Object", null
  if %440065 : bool then %440064 else %440063
%440063:
  %440066 = load[ptr] %340002 : @"T16java.lang.Object"
  %440067 = elem[{ptr, ptr, int, int, ptr}] %440066 : ptr, int 0, int 2
  %440068 = load[int] %440067 : ptr
  %440069 = sle[int] int 206, %440068 : int
  %440070 = sle[int] %440068 : int, int 209
  %440071 = and[bool] %440069 : bool, %440070 : bool
  if %440071 : bool then %440064 else %440055(%340002 : @"T16java.lang.Object", @"M21java.lang.ThreadLocalG4type" : ptr)
%440064:
  %340003 = bitcast[@"T21java.lang.ThreadLocal"] %340002 : @"T16java.lang.Object"
  %340005 = ine[@"T16java.lang.Object"] %340003 : @"T21java.lang.ThreadLocal", null
  if %340005 : bool then %360000 else %370000
%360000:
  %360002 = iadd[int] %220002 : int, int 1
  %440074 = ine[array[@"T16java.lang.Object"]] %150001 : array[@"T16java.lang.Object"], null
  if %440074 : bool then %440073 else %440002
%440073:
  %440075 = elem[{ptr, ptr, int, int, [nothing x 0]}] %150001 : array[@"T16java.lang.Object"], int 0, int 2
  %440072 = load[int] %440075 : ptr
  %440077 = sge[int] %360002 : int, int 0
  %440078 = slt[int] %360002 : int, %440072 : int
  %440079 = and[bool] %440077 : bool, %440078 : bool
  if %440079 : bool then %440076 else %440044(%360002 : int)
%440076:
  %440080 = elem[{ptr, ptr, int, int, [@"T16java.lang.Object" x 0]}] %150001 : array[@"T16java.lang.Object"], int 0, int 4, %360002 : int
  %360003 = load[@"T16java.lang.Object"] %440080 : ptr
  %440081 = call[(@"T28java.lang.ThreadLocal$Values", @"T21java.lang.ThreadLocal", @"T16java.lang.Object") => unit] @"M28java.lang.ThreadLocal$ValuesD3addL21java.lang.ThreadLocalL16java.lang.ObjectuEO" : ptr(%1 : @"T28java.lang.ThreadLocal$Values", %340003 : @"T21java.lang.ThreadLocal", %360003 : @"T16java.lang.Object")
  jump %380000
%370000:
  %440083 = ine[@"T28java.lang.ThreadLocal$Values"] %1 : @"T28java.lang.ThreadLocal$Values", null
  if %440083 : bool then %440082 else %440002
%440082:
  %440084 = elem[{ptr, ptr, array[@"T16java.lang.Object"], int, int, int}] %1 : @"T28java.lang.ThreadLocal$Values", int 0, int 5
  %420001 = load[int] %440084 : ptr
  %430001 = isub[int] %420001 : int, int 1
  %440087 = ine[@"T28java.lang.ThreadLocal$Values"] %1 : @"T28java.lang.ThreadLocal$Values", null
  if %440087 : bool then %440086 else %440002
%440086:
  %440088 = elem[{ptr, ptr, array[@"T16java.lang.Object"], int, int, int}] %1 : @"T28java.lang.ThreadLocal$Values", int 0, int 5
  %440085 = store[int] %440088 : ptr, %430001 : int
  jump %380000
%380000:
  jump %410000
%350000:
  jump %410000
%410000:
  %410002 = isub[int] %220002 : int, int 2
  jump %220000(%330001 : bool, %410002 : int, %220003 : int)
%240000:
  jump %440000
%440000:
  %440089 = call[() => unit] @"M2__C22scalanative_yieldpoint" : () => unit()
  ret true
%440002:
  %440090 = call[(@"T34scala.scalanative.runtime.package$") => nothing] @"M34scala.scalanative.runtime.package$D16throwNullPointernEO" : ptr(null)
  unreachable
%440055(%440091 : ptr, %440092 : ptr):
  %440093 = load[ptr] %440091 : ptr
  %440094 = call[(@"T34scala.scalanative.runtime.package$", ptr, ptr) => nothing] @"M34scala.scalanative.runtime.package$D14throwClassCastR_R_nEO" : ptr(null, %440093 : ptr, %440092 : ptr)
  unreachable
%440044(%440095 : int):
  %440096 = call[(ptr, int) => nothing] @"M34scala.scalanative.runtime.package$D16throwOutOfBoundsinEO" : ptr(null, %440095 : int)
  unreachable
}