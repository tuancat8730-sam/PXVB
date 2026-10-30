import { useEffect, useState } from 'react';
import { ControlPanel } from '../../components/ui/ControlPanel';
import { StateNotice } from '../../components/ui/StateNotice';
import { describeApiError } from '../../services/apiError';
import { getRecord, updateRecord } from '../../services/md/catalogApi';
import type { CatalogRecord } from '../../services/md/types';
import { addField, moveField, parseFormSchema, schemaProblems, type FormField, type FormSchema } from '../../lib/kscl/formSchema';
import { readDate, readNumber, readText } from '../../lib/record';
import { KsclAddFieldForm } from './KsclAddFieldForm';
import { KsclFieldTable } from './KsclFieldTable';

const RESOURCE = 'qc-form-templates';
const UNREADABLE_SCHEMA_MESSAGE = 'Lược đồ của biểu mẫu này không đọc được.';

interface KsclDesignerScreenProps {
  recordId?: string | null;
}

interface LoadedForm {
  record: CatalogRecord;
  schema: FormSchema;
}

type LoadState = { status: 'idle' } | { status: 'loading' } | { status: 'ready'; form: LoadedForm } | { status: 'error'; message: string };

// U-1: trình thiết kế lược đồ KSCL. Lỗi lược đồ hiện trước khi lưu; có lỗi thì không cho lưu.
export function KsclDesignerScreen({ recordId = null }: KsclDesignerScreenProps) {
  const [load, setLoad] = useState<LoadState>({ status: 'idle' });
  const [schema, setSchema] = useState<FormSchema | null>(null);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    if (recordId === null) {
      return;
    }
    let isCurrent = true;
    setLoad({ status: 'loading' });
    setSaveMessage(null);
    setSaveError(null);

    getRecord(RESOURCE, recordId)
      .then((record) => {
        if (!isCurrent) {
          return;
        }
        const parsed = parseFormSchema(record.formSchema);
        if (parsed === null) {
          setLoad({ status: 'error', message: UNREADABLE_SCHEMA_MESSAGE });
          return;
        }
        setSchema(parsed);
        setLoad({ status: 'ready', form: { record, schema: parsed } });
      })
      .catch((error: unknown) => {
        if (isCurrent) {
          setLoad({ status: 'error', message: describeApiError(error) });
        }
      });

    return () => {
      isCurrent = false;
    };
  }, [recordId]);

  if (recordId === null) {
    return (
      <section className="screen">
        <StateNotice kind="empty" message="Chọn một biểu mẫu ở mục Biểu mẫu KSCL để thiết kế." />
      </section>
    );
  }

  if (load.status === 'loading' || load.status === 'idle') {
    return (
      <section className="screen">
        <StateNotice kind="loading" />
      </section>
    );
  }

  if (load.status === 'error' || schema === null) {
    return (
      <section className="screen">
        <StateNotice kind="error" message={load.status === 'error' ? load.message : UNREADABLE_SCHEMA_MESSAGE} />
      </section>
    );
  }

  const { record } = load.form;
  const problems = schemaProblems(schema);
  const versionNo = readNumber(record, 'versionNo');
  const isDraft = readText(record, 'status') === 'DRAFT';
  const title = `Trình thiết kế ${readText(record, 'code')} (${isDraft ? 'nháp ' : ''}v${versionNo})`;

  function handleMove(sectionIndex: number, fieldIndex: number, delta: -1 | 1) {
    setSchema((current) => (current ? moveField(current, sectionIndex, fieldIndex, delta) : current));
  }

  function handleAdd(sectionIndex: number, field: FormField) {
    setSchema((current) => (current ? addField(current, sectionIndex, field) : current));
  }

  async function handleSave() {
    setSaveMessage(null);
    setSaveError(null);
    try {
      await updateRecord(RESOURCE, readText(record, 'id'), { formSchema: schema });
      setSaveMessage('Đã lưu nháp.');
    } catch (error: unknown) {
      setSaveError(describeApiError(error));
    }
  }

  return (
    <section className="screen" aria-labelledby="ksc-designer-title">
      <header className="screen-header">
        <p className="screen-crumb">Danh mục / Biểu mẫu KSCL</p>
        <h1 id="ksc-designer-title">{title}</h1>
      </header>

      <div className="screen-split">
        <div className="screen-main">
          <div className="card designer-card">
            <KsclFieldTable schema={schema} onMove={handleMove} />
            <KsclAddFieldForm sections={schema.sections} onAdd={handleAdd} />

            {problems.length > 0 && (
              <div className="state-notice state-notice--error" role="alert">
                <p>Chưa lưu được lược đồ:</p>
                <ul>
                  {problems.map((problem) => (
                    <li key={problem}>{problem}</li>
                  ))}
                </ul>
              </div>
            )}
            {saveMessage && <StateNotice kind="success" message={saveMessage} />}
            {saveError && <StateNotice kind="error" message={saveError} />}

            <div className="designer-actions">
              <button type="button" className="btn-primary" disabled={problems.length > 0} onClick={handleSave}>
                Lưu nháp
              </button>
            </div>
          </div>
        </div>

        <ControlPanel
          owner={readText(record, 'owner')}
          updatedAt={readDate(record, 'updatedAt')}
          version={versionNo}
          status={readText(record, 'status')}
        />
      </div>
    </section>
  );
}
